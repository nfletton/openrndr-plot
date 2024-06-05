import org.openrndr.color.ColorRGBa
import org.openrndr.extra.composition.*
import org.openrndr.extra.noise.Random
import org.openrndr.extra.svg.saveToFile
import org.openrndr.math.Vector2
import org.openrndr.math.transforms.transform
import org.openrndr.panel.elements.round
import org.openrndr.shape.*
import java.io.File
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.min

const val DEFAULT_OPTIONS =
    "# SE/A3\n" + "options model 2\n" + "# brushless servo\n" + "#options penlift 3\n" +
            "# millimeter unit\n" + "options units 2\n" + "# max. safe pen up 67\n" +
            "options pen_pos_up 45\n" + "options pen_pos_down 30\n" + "options speed_pendown 25\n"

const val DEFAULT_LAYER_NAME = "base"

enum class DrawTool {
    PEN,           // a pen that may need swapping out or refilling
    DIP,           // tool requiring regular dipping in a well
    DIP_AND_STIR,  // tool requiring regular dipping in a well with medium that need stirring
}

/**
 * PlotConfig represents the configuration settings for controlling the AxiDraw plotter.
 *
 * @property toolType   The type of drawing tool
 * @property displayScale   The scaling factor of the on-screen display of the sketch
 * @property pathTolerance   The tolerance for what are considered connected paths
 * @property stepResolution The resolution of a step in plotter movement in millimetres
 * @property refillDistance   The stroke length before the drawing medium needs reloading in mm.
 * @property refillTolerance
 * @property preOptions    AxiDraw options to run before plot begins
 * @property randomizeStart    Randomize the start of closed paths
 * @property paperSize    The dimensions of the plotting area
 * @property palette   Color palette. All colors must have alpha of 1.0.
 * @property paperOffset   Paper position relative to the AxiDraw home position
 * @property paintWells    A map of [ColorRGBa] to a list of [Rectangle]s
 * @property washWells    List of [Rectangle]s defining the wash wells
 * @property wellPadding    The padding between the well sides and the stirring path in mm
 * @property paintStirStrokes    The number of strokes in a paint well when stirring the drawing medium
 * @property washStirStrokes    The number of strokes in a wash well when washing the drawing tool
 * @property axiDrawTravel    The xy travel limits for specific AxiDraw models
 */
data class PlotConfig(
    val toolType: DrawTool = DrawTool.PEN,
    val displayScale: Double = 1.0,
    val pathTolerance: Double = 0.5,
    val stepResolution: Double = 0.5,
    val refillDistance: Double = Double.POSITIVE_INFINITY,
    val refillTolerance: Double = 5.0,
    val preOptions: String = DEFAULT_OPTIONS,
    val randomizeStart: Boolean = true,
    val paperSize: PaperSize = PaperSize.ART_9x12,
    val palette: Map<ColorRGBa, String> = emptyMap(),
    val paperOffset: Vector2 = Vector2.ZERO,
    val paintWells: Map<ColorRGBa, List<Rectangle>> = emptyMap(),
    val washWells: List<Rectangle> = emptyList(),
    val wellPadding: Double = 4.0,
    val paintStirStrokes: Int = 4,
    val washStirStrokes: Int = 4,
    val axiDrawTravel: AxiDrawTravel = AxiDrawTravel.V3A3,
) {
    init {
        require(eachColorHasAWell()) { "Each color in the palette must have an associated well" }
    }

    fun eachColorHasAWell() =
        if (toolType == DrawTool.DIP || toolType == DrawTool.DIP_AND_STIR) {
            palette.all { (color, _) -> paintWells.containsKey(color) }
        } else true

    val requiresRefills: Boolean
        get() = refillDistance < Double.POSITIVE_INFINITY

    val requiresWash: Boolean
        get() = washWells.isNotEmpty() && (toolType == DrawTool.DIP_AND_STIR || toolType == DrawTool.DIP)

    val requiresStir: Boolean
        get() = toolType == DrawTool.DIP_AND_STIR

    val multiColor: Boolean
        get() = palette.isNotEmpty()
}

internal typealias SegmentColorGroups = MutableMap<ColorRGBa, MutableList<Segment2D>>
internal typealias SegmentLayers = MutableMap<String, SegmentColorGroups>

internal typealias PathColorGroups = MutableMap<ColorRGBa, MutableList<Path>>
internal typealias PathLayers = MutableMap<String, PathColorGroups>

data class WellCommand(val location: Vector2, val commandName: String, val command: String)

/**
 * Extract the segments that comprise the shape.
 */
internal fun Shape.extractSegments(displayScale: Double, paperPosition: Vector2): List<Segment2D> {
    val segments = mutableListOf<Segment2D>()

    val scaledContours = shape.transform(transform {
        translate(paperPosition)
        scale(1 / displayScale)
    })

    scaledContours.contours.forEach { contour ->
        segments += contour.segments
    }
    return segments
}

/**
 * Represents a path consisting of one or more points.
 * Each path is a contiguous set of points that is plotted
 * as a single line or in the case of a single point, is plotted
 * as a pen down followed by a pen up.
 *
 * @property points The list of points that make up the path.
 */
internal class Path(
    var points: List<Vector2>,
) {
    fun distanceToOtherStart(other: Path): Double = points.last().distanceTo(other.points.first())

    fun distanceToOtherEnd(other: Path): Double = points.last().distanceTo(other.points.last())

    fun closestEnd(other: Path): Double =
        min(this.distanceToOtherStart(other), this.distanceToOtherEnd(other))

    fun length() = points.windowed(2).fold(0.0) { sum, segment ->
        sum + segment.first().distanceTo(segment.last())
    }

    fun closed(): Boolean = points.size > 2 && points.first() == points.last()

    /**
     * Shifts the start point of the path by a random offset or a specified offset.
     */
    fun shiftStart(offset: Int = Random.int(0, (points.size - 1))) {
        val modOffset = offset.absoluteValue % (points.size - 1)
        if (closed() && modOffset != 0) {
            val openPathSize = points.size - 1
            val shiftedPoints = List(openPathSize) { i ->
                points[(i + modOffset) % openPathSize]
            }
            points = shiftedPoints + shiftedPoints.first()
        }
    }
}

internal class RefillData(val config: PlotConfig) {
    val stirPaths: Map<ColorRGBa, List<List<Vector2>>>
    val washPaths: List<List<Vector2>>
    val refillCommands: Map<ColorRGBa, List<WellCommand>>
    val washCommands: List<WellCommand>

    init {
        stirPaths = if (config.requiresStir)
            config.paintWells.asIterable().associate { (color, wells) ->
                color to generateStirPaths(wells, config.paintStirStrokes, config.wellPadding)
            }
        else emptyMap()

        washPaths = if (config.requiresWash)
            generateStirPaths(
                config.washWells, config.washStirStrokes, config.wellPadding
            )
        else emptyList()

        refillCommands = generateRefillCommands(config.paintWells)
        washCommands = generateWashCommands(config.washWells)
    }

    /**
     * Generates wash well stir paths.
     *
     * @param wells The list of rectangles representing the wells.
     * @param strokes The number of strokes to be performed within each well.
     * @param padding The padding to be applied around the inside of each well.
     * @return The list of stir paths for each well.
     */
    private fun generateStirPaths(
        wells: List<Rectangle>, strokes: Int, padding: Double
    ): List<List<Vector2>> = wells.map { generateStirPathPoints(it, strokes, padding) }

    /**
     * Generates the points of a stir path within a well.
     *
     * @param well The shape of the well.
     * @param strokes The number of strokes to be performed within the well.
     * @param padding The padding to be applied around the inside of the well.
     * @return The list of points making up the stir path.
     */
    private fun generateStirPathPoints(
        well: Rectangle, strokes: Int, padding: Double
    ): List<Vector2> {
        val washApexes = ceil(strokes / 2.0).toInt()
        val stirBounds = well.offsetEdges(-padding)
        val edge1Points = stirBounds.contour.segments[1].equidistantPositions(washApexes)
        val edge2Points = stirBounds.contour.segments[3].equidistantPositions(washApexes)
        return listOf(well.center) + edge1Points.zip(edge2Points.reversed())
            .flatMap { listOf(it.first, it.second) } + well.center
    }

    /**
     * Generates refill commands for each color and well.
     *
     * @param wells The list of pairs containing the color and rectangle of each well.
     * @param colors The list of colors.
     * @return The list of refill commands, each consisting of a Vector2 position,
     * command name, and AxiDraw command definition.
     */
    private fun generateRefillCommands(
        wells: Map<ColorRGBa, List<Rectangle>>
    ): Map<ColorRGBa, List<WellCommand>> =
        wells.asIterable().associate { (color, wells) ->
            color to List(wells.size) { wellIndex ->
                generateWellPathCommand("refill_${config.palette[color]}_w${wellIndex}", stirPaths[color]!![wellIndex])
            }
        }

    /**
     * Generates wash commands for the given list of wells.
     *
     * @param wells The list of rectangles representing the wells.
     * @return The list of wash commands, each consisting of a Vector2 position,
     * command name, and AxiDraw command definition.
     */
    private fun generateWashCommands(
        wells: List<Rectangle>
    ): MutableList<WellCommand> {
        val output: MutableList<WellCommand> = mutableListOf()
        wells.forEachIndexed { wellIndex, _ ->
            output.add(
                generateWellPathCommand("wash_w${wellIndex}", washPaths[wellIndex])
            )
        }
        return output
    }

    /**
     * Generates an AxiDraw well path command definition.
     *
     * @param cmdName The name of the AxiDraw well path command.
     * @param path The list of points on the path.
     * @return A triple containing the starting position, the command name, and the command definition.
     */
    private fun generateWellPathCommand(
        cmdName: String, path: List<Vector2>
    ): WellCommand {
        val cmdDef = "draw_path ${roundAndStringify(path)}"
        return WellCommand(path.first(), cmdName, cmdDef)
    }

    /**
     * Returns the command string for the nearest wash well based on the location of the pen.
     */
    fun getNearestWashWellCmd(location: Vector2): String {
        var closestWell = Double.POSITIVE_INFINITY
        var cmd = "# well not found for color"
        washCommands.forEach { (wellLocation, command, _) ->
            val distanceToWell = location.distanceTo(wellLocation)
            if (distanceToWell < closestWell) {
                closestWell = distanceToWell
                cmd = command
            }
        }
        return "$cmd\n"
    }

    /**
     * Returns the command name for the paint well based on the location of the pen.
     */
    fun getNearestPaintWellCmd(color: ColorRGBa, location: Vector2): String {
        var closestWell = Double.POSITIVE_INFINITY
        var cmd = "# well not found for color"
        refillCommands[color]?.forEach { (wellLocation, command, _) ->
            val distanceToWell = location.distanceTo(wellLocation)
            if (distanceToWell < closestWell) {
                closestWell = distanceToWell
                cmd = command
            }
        }
        return "$cmd\n"
    }

    /**
     * Builds a string containing all the command definitions
     * for the AXiDraw output file.
     *
     * @return The string containing the command definitions.
     */
    fun cmdDefinitions(): String {
        val sb = StringBuilder()
        refillCommands.forEach {
            it.value.forEach { (_, cmd, definition) ->
                sb.append("def $cmd $definition\n")
            }
        }
        washCommands.forEach { (_, cmd, definition) ->
            sb.append("def $cmd $definition\n")
        }
        return sb.toString()
    }
}


/**
 * Saves the AxiDraw file set for the given composition and plot configuration.
 * The files include: the intermediate command file to drive the AxiDraw, an SVG of the
 * plot surface layout and the SVG file of the plot itself
 *
 * @param baseFilename The base name of the file set to be saved.
 * @param config The plot configuration settings for controlling the AxiDraw plotter.
 */
fun Composition.saveAxiDrawFileSet(baseFilename: String, config: PlotConfig) {
    val groupedSegments =
        groupSegmentsByLayerAndColor(
            this,
            config.displayScale,
            config.paperOffset
        )
    orderSegments(groupedSegments)
    val paths = generatePaths(groupedSegments, config)

    val refillData = RefillData(config)

    val axiDrawFile = File("${baseFilename}.txt")
    axiDrawFile.writeText(generatePlotData(paths, refillData, config))
    saveLayoutToSvgFile(baseFilename, refillData, config)
    savePlotToSvgFile(config.displayScale, baseFilename)
}


/**
 * Saves the composition to an SVG file and, assuming the onscreen display dimensions are
 * based on paper size (i) it scales the SVG document to the desired paper
 * size (ii) scales the sketch to the paper size.
 *
 * @param displayScale The scaling factor of the on-screen display of the sketch.
 * @param filename The name of the SVG file to be saved.
 */
fun Composition.savePlotToSvgFile(
    displayScale: Double, filename: String
) {
    val origTransform = root.transform
    val origWidth = style.width
    val origHeight = style.height
    root.transform = transform { scale(96 / 25.4 / displayScale) }
    style.width = Length.Pixels.fromMillimeters(style.width.value / displayScale)
    style.height = Length.Pixels.fromMillimeters(style.height.value / displayScale)
    saveToFile(File("$filename.svg"))
    root.transform = origTransform
    style.width = origWidth
    style.height = origHeight
}

/**
 * Generates a string of plot data that includes command definitions,
 * AxiDraw options, AxiDraw functions, wash commands and refill commands for each
 * layer and color group.
 *
 * @return The string containing the AxiDraw plot data.
 */
internal fun generatePlotData(
    pathLayers: PathLayers,
    refillData: RefillData,
    config: PlotConfig
): String {
    val sb = StringBuilder()
    sb.append("${refillData.cmdDefinitions()}\n")
    sb.append("${config.preOptions}\nupdate\n")

    sb.append("penup\n")
    var location = Vector2.ZERO
    pathLayers.forEach { (layerName, layer) ->
        sb.append("# Layer: ${layerName}\n")
        layer.forEach { (color, paths) ->
            if (paths.isNotEmpty()) {
                if (config.toolType == DrawTool.PEN)
                    sb.append("pause Color Change to ${config.palette[color]}\n")
                if (config.requiresRefills) {
                    sb.append(
                        writeStrokesAndRefills(paths, refillData, config, color, location)
                    )
                } else {
                    // write paths directly
                    paths.forEach {
                        sb.append("draw_path ${roundAndStringify(it.points)}\n")
                    }
                }
                location = paths.last().points.last()
            }
        }
    }
    sb.append("moveto 0.0 0.0\n")
    return sb.toString()
}

/**
 * Writes the strokes and refills for the given paths, config, color, and lastLocation.
 *
 * @param paths The list of paths to process.
 * @param config The plot configuration settings.
 * @param color The color of the strokes.
 * @param lastLocation The last location of the plotter pen.
 * @return The string containing the strokes and refills.
 */
private fun writeStrokesAndRefills(
    paths: MutableList<Path>,
    refillData: RefillData,
    config: PlotConfig,
    color: ColorRGBa,
    lastLocation: Vector2
): String {
    // TODO: Add comment that all segments are less than or equal to PlotConfig.refillDistance
    val strokes: MutableList<List<Vector2>> = mutableListOf()
    val sb: StringBuilder = StringBuilder()

    paths.forEach { path ->
        val strokePoints = mutableListOf<Vector2>()
        var distanceSoFar = 0.0
        val points = path.points
        strokePoints += points.first()
        var lastPoint = points.first()
        for (pointIndex in 1..points.lastIndex) {
            val currentPoint = points[pointIndex]
            val distanceFromLast = lastPoint.distanceTo(currentPoint)
            if (distanceSoFar + distanceFromLast > config.refillDistance) {
                strokes += strokePoints.toList()
                strokePoints.clear()
                distanceSoFar = 0.0
                strokePoints += lastPoint
            }
            strokePoints += currentPoint
            lastPoint = currentPoint
            distanceSoFar += distanceFromLast
            if (pointIndex == points.lastIndex) {
                strokes += strokePoints
            }
        }
    }
    var currentLocation = lastLocation
    if (config.requiresWash) sb.append(refillData.getNearestWashWellCmd(currentLocation))
    strokes.forEach { stroke ->
        if (config.requiresRefills) {
            sb.append(refillData.getNearestPaintWellCmd(color, currentLocation))
        }
        sb.append("draw_path ${roundAndStringify(stroke)}\n")
        currentLocation = stroke.last()
    }
    return sb.toString()
}

/**
 * Groups segments of ShapeNodes by layer and color.
 *
 * @return A Map representing the plot layers, where each layer contains a map of
 *         colors and their corresponding segments.
 */
internal fun groupSegmentsByLayerAndColor(
    composition: Composition,
    displayScale: Double,
    paperOffset: Vector2
): SegmentLayers {
    val segmentLayers: SegmentLayers = mutableMapOf(DEFAULT_LAYER_NAME to mutableMapOf())
    var currentLayer = DEFAULT_LAYER_NAME
    composition.root.visitAll {
        if (this is ShapeNode) {
            val currentColor = this.stroke ?: ColorRGBa.BLACK
            if (this.attributes.containsKey("layer")) {
                currentLayer = this.attributes["layer"] ?: currentLayer
            }
            if (!segmentLayers.containsKey(currentLayer)) {
                segmentLayers[currentLayer] = mutableMapOf(currentColor to mutableListOf())
            } else {
                if (!segmentLayers[currentLayer]!!.containsKey(currentColor)) {
                    segmentLayers[currentLayer]!![currentColor] = mutableListOf()
                }
            }
            segmentLayers[currentLayer]?.get(currentColor)!! +=
                this.shape.extractSegments(displayScale, paperOffset)
        }
    }
    return segmentLayers
}

/**
 * Orders the segments within each layer / color group to minimize plotter travel.
 * Probably of little value when using a drawing tool requiring regular dipping.
 *
 */
internal fun orderSegments(segmentLayers: SegmentLayers) {
    segmentLayers.forEach { (_, layer) ->
        layer.forEach { (color, segments) ->
            if (segments.isNotEmpty()) {
                val ordered = mutableListOf<Segment2D>()
                while (segments.isNotEmpty()) {
                    val lastPoint: Vector2 =
                        if (ordered.isEmpty()) Vector2.ZERO else ordered.last().end
                    val closestStart = segments.reduce { closest, other ->
                        other.takeIf {
                            it.start.squaredDistanceTo(lastPoint) < closest.start.squaredDistanceTo(
                                lastPoint
                            )
                        } ?: closest
                    }
                    val closestEnd = segments.reduce { closest, other ->
                        other.takeIf {
                            it.end.squaredDistanceTo(lastPoint) < closest.end.squaredDistanceTo(
                                lastPoint
                            )
                        } ?: closest
                    }
                    if (lastPoint.squaredDistanceTo(closestStart.start) < lastPoint.squaredDistanceTo(
                            closestEnd.end
                        )
                    ) {
                        segments.remove(closestStart)
                        ordered.add(closestStart)
                    } else {
                        segments.remove(closestEnd)
                        ordered.add(closestEnd.reverse)
                    }
                }
                layer[color] = ordered
            }
        }
    }
}

/**
 * Generates equidistant points along a given segment based on a step resolution.
 * If the segment length is greater than the step resolution, the segment is divided into
 * equidistant positions and the points are returned. Otherwise, only the start and end points
 * of the segment are returned.
 *
 * @param segment The segment to generate points from.
 * @param stepResolution The resolution at which points should be generated.
 * @return A list of Vector2 points along the segment.
 */
private fun generateCurveSteps(
    segment: Segment2D, stepResolution: Double
): List<Vector2> {
    val length = segment.length
    val points: List<Vector2>
    if (length > stepResolution) {
        val numSteps: Int = ceil(length / stepResolution).toInt()
        points = segment.equidistantPositions(numSteps)
    } else {
        points = listOf(segment.start, segment.end)
    }
    return points.map {
        Vector2(it.x, it.y)
    }
}

/**
 * Generates point paths from the OPENRNDR `Segments`. The distance
 * between adjacent points on the created paths do not exceed
 * [refillDistance][lib.PlotConfig.refillDistance] Points from successive segments are
 * joined into a single set of points if the distance between them is less than
 * [pathTolerance][lib.PlotConfig.pathTolerance]
 */
internal fun generatePaths(segmentLayers: SegmentLayers, config: PlotConfig): PathLayers {
    val pathLayers = mutableMapOf<String, PathColorGroups>()
    segmentLayers.forEach { (layerName, layer) ->
        pathLayers[layerName] = mutableMapOf()
        layer.forEach { (color, segments) ->
            pathLayers[layerName]!![color] = mutableListOf()
            var points = mutableListOf<Vector2>()
            val paths = mutableListOf<Path>()
            segments.forEach { segment ->
                val segPoints: List<Vector2> = when (segment.control.size) {
                    // TODO: handle dots - these should be pen up down operations only
                    1, 2 -> generateCurveSteps(segment, config.stepResolution)
                    else -> {
                        if (segment.length <= config.refillDistance) {
                            listOf(segment.start, segment.end)
                        } else {
                            val numStrokes =
                                ceil(segment.length / config.refillDistance).toInt()
                            segment.equidistantPositions(numStrokes + 1)
                        }
                    }
                }
                segPoints.forEachIndexed { pIndex, point ->
                    if (pIndex == 0 && points.isNotEmpty()) {
                        if (points.last().distanceTo(point) > config.pathTolerance) {
                            paths += Path(points)
                            points = mutableListOf(point)
                        }
                    } else {
                        points += point
                    }
                }
            }
            if (points.isNotEmpty()) {
                val path = Path(points)
                if (config.randomizeStart && path.closed()) path.shiftStart()
                paths += path
                points = mutableListOf()
            }
            pathLayers[layerName]?.set(color, paths)
        }
    }
    return pathLayers
}

/**
 * Saves the layout of the plot to an SVG file.
 * The layout includes: The paint and wash wells,
 * the paint and wash stir paths; and the paper boundary
 */
internal fun saveLayoutToSvgFile(filename: String, refillData: RefillData, config: PlotConfig) {
    val layout = drawComposition {
        strokeWeight = 0.5

        composition(createPaletteLayout(config.paintWells, config.washWells))
        refillData.stirPaths.forEach { (_, paths) ->
            paths.forEach {
                contour(ShapeContour.fromPoints(it, closed = false))
            }
        }
        refillData.washPaths.forEach {
            contour(ShapeContour.fromPoints(it, closed = false))
        }

        rectangle(
            config.paperOffset.x,
            config.paperOffset.y,
            config.paperSize.height,
            config.paperSize.width
        )
    }
    layout.root.transform = transform { scale(96 / 25.4) }
    layout.style.width = Length.Pixels.fromMillimeters(config.axiDrawTravel.x)
    layout.style.height = Length.Pixels.fromMillimeters(config.axiDrawTravel.y)
    layout.saveToFile(File("${filename}_Layout.svg"))
}

/**
 * Creates a composition representing the layout of palette wells defined
 * by [PlotConfig.paintWells] and [PlotConfig.washWells].
 *
 * @property paintWells   Map of paint wells
 * @property washWells    List of wash wells
 * @return The composition representing the palette layout.
 */
private fun createPaletteLayout(
    paintWells: Map<ColorRGBa, List<Rectangle>>,
    washWells: List<Rectangle>
): Composition {
    val composition = drawComposition {
        strokeWeight = 0.5
        paintWells.forEach { (color, wells) ->
            wells.forEach { well ->
                fill = color
                rectangle(well)
            }
        }
        washWells.forEach {
            fill = null
            rectangle(it)
        }
    }
    return composition
}

/**
 * Rounds the coordinates of each Vector2 in the given list to the specified number of decimals
 * and converts the result to a string representation with no embedded whitespace.
 *
 * @param points The list of Vector2 points to round and stringify.
 * @param decimals The number of decimal places to round the coordinates to.
 * @return The string representation of the rounded Vector2 points.
 */
internal fun roundAndStringify(points: List<Vector2>, decimals: Int = 3): String =
    points.map {
        "[${it.x.round(decimals)},${it.y.round(decimals)}]"
    }.toString().replace("\\s".toRegex(), "")

private fun Double.isApproximately(other: Double, tolerance: Double = 0.001): Boolean {
    return abs(this - other) <= tolerance
}
