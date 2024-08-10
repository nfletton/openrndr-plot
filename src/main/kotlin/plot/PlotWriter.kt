package plot

import io.github.oshai.kotlinlogging.KotlinLogging
import org.openrndr.color.ColorRGBa
import org.openrndr.extra.composition.*
import org.openrndr.extra.noise.Random
import org.openrndr.extra.svg.saveToFile
import org.openrndr.math.Vector2
import org.openrndr.math.transforms.transform
import org.openrndr.panel.elements.round
import org.openrndr.shape.Rectangle
import org.openrndr.shape.Segment2D
import org.openrndr.shape.Shape
import org.openrndr.shape.ShapeContour
import java.io.File
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.ceil
import kotlin.math.min


val logger = KotlinLogging.logger { }

private const val DEFAULT_OPTIONS =
    "# SE/A3\n" + "options model 2\n" + "# brushless servo\n" + "#options penlift 3\n" +
            "# millimeter unit\n" + "options units 2\n" + "# max. safe pen up 67\n" +
            "options pen_pos_up 48\n" + "options pen_pos_down 38\n" + "options speed_pendown 25\n" + "options speed_penup 50\n"

private const val DEFAULT_LAYER_NAME = "base"

private const val CONVERSION_FACTOR = 96 / 25.4


enum class DrawTool(val description: String) {
    Pen("A pen that may optionally need swapping out or refilling"),
    Dip("A tool requiring regular dipping in one or more wells"),
    DipAndStir("A tool requiring regular dipping in a well with medium that need stirring"),
}

/**
 * PlotConfig represents the configuration settings for controlling the AxiDraw plotter.
 *
 * @property toolType   The type of drawing tool
 * @property displayScale   The scaling factor from paper size to on-screen sketch dimensions
 * @property pathTolerance   The tolerance for what are considered connected paths
 * @property stepResolution The resolution of a step in plotter movement in millimetres
 * @property refillDistance   The stroke length before the drawing medium needs reloading in mm.
 * @property refillTolerance
 * @property duplicateTolerance    Tolerance for removing wholly overlapping segments.
 * @property preOptions    AxiDraw options to run before the plot begins
 * @property randomizeStart    Randomize the start point of closed paths
 * @property paperSize    The dimensions of the plotting area
 * @property palette   Color palette. All colors must have alpha of 1.0.
 * @property paperOffset   Paper position relative to the AxiDraw home position
 * @property paintWells    A map of [ColorRGBa] to a list of [Rectangle]s
 * @property washWells    List of [Rectangle]s defining the wash wells
 * @property wellPadding    The padding between the sides of the wells and the stirring path in mm
 * @property paintStirStrokes    The number of strokes in a paint well when stirring the drawing medium
 * @property washStirStrokes    The number of strokes in a wash well when washing the drawing tool
 * @property axiDrawTravel    The xy travel limits for specific AxiDraw models
 */
data class PlotConfig(
    val toolType: DrawTool = DrawTool.Pen,
    val displayScale: Double = 1.0,
    val pathTolerance: Double = 0.5,
    val stepResolution: Double = 0.5,
    val refillDistance: Double = Double.POSITIVE_INFINITY,
    val refillTolerance: Double = 5.0,
    val preOptions: String = DEFAULT_OPTIONS,
    val randomizeStart: Boolean = true,
    val duplicateTolerance: Double = Double.POSITIVE_INFINITY,
    val paperSize: PaperSize = PaperSize.ART_9x12,
    val palette: Map<ColorRGBa, String> = mapOf(ColorRGBa.BLACK to "black"),
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
        if (toolType == DrawTool.Dip || toolType == DrawTool.DipAndStir) {
            palette.all { (color, _) -> paintWells.containsKey(color) }
        } else true

    val requiresRefills: Boolean
        get() = refillDistance < Double.POSITIVE_INFINITY

    val requiresWash: Boolean
        get() = washWells.isNotEmpty() && (toolType == DrawTool.DipAndStir || toolType == DrawTool.Dip)

    val requiresStir: Boolean
        get() = toolType == DrawTool.DipAndStir

    val isPainting: Boolean
        get() = toolType == DrawTool.Dip || toolType == DrawTool.DipAndStir
}

internal typealias SegmentColorGroups = MutableMap<ColorRGBa, MutableList<Segment2D>>
internal typealias SegmentLayers = MutableMap<String, SegmentColorGroups>

internal typealias PathColorGroups = MutableMap<ColorRGBa, MutableList<Path>>
internal typealias PathLayers = MutableMap<String, PathColorGroups>

data class WellCommand(val location: Vector2, val commandName: String, val command: String)

/**
 * Extract segments from shape while scaling and offsetting them according to on-screen scaling
 * and paper position on the plot surface.
 */
internal fun Shape.extractScaledSegments(
    displayScale: Double,
    paperPosition: Vector2
): List<Segment2D> {
    val scaledContours = this.transform(transform {
        translate(paperPosition)
        scale(1 / displayScale)
    })
    return scaledContours.contours.flatMap { it.segments }
}

/**
 * Represents a path consisting of one or more points.
 * Each path is a contiguous set of points that is plotted
 * as a single line or in the case of a single point, is plotted
 * as a pen down followed by a pen up.
 */
internal class Path(var points: List<Vector2>) {
    fun distanceToOtherStart(other: Path): Double = points.last().distanceTo(other.points.first())

    fun distanceToOtherEnd(other: Path): Double = points.last().distanceTo(other.points.last())

    fun closestEnd(other: Path): Double =
        min(this.distanceToOtherStart(other), this.distanceToOtherEnd(other))

    fun length() = points.windowed(2).fold(0.0) { sum, segment ->
        sum + segment.first().distanceTo(segment.last())
    }

    fun closed(): Boolean = points.size > 2 && points.first() == points.last()

    /**
     * Rotate the points of the path by an offset.
     */
    fun rotatePoints(offset: Int = Random.int(0, points.lastIndex)) {
        val modOffset = offset.absoluteValue % points.lastIndex
        if (closed() && modOffset != 0) {
            val shiftedPoints = List(points.lastIndex) { i ->
                points[(i + modOffset) % points.lastIndex]
            }
            points = shiftedPoints + shiftedPoints.first()
        }
    }
}

internal class RefillData(private val config: PlotConfig) {
    val stirPaths: Map<ColorRGBa, List<List<Vector2>>>
    val washPaths: List<List<Vector2>>
    private val refillCommands: Map<ColorRGBa, List<WellCommand>>
    private val washCommands: List<WellCommand>

    init {
        stirPaths = if (config.requiresStir)
            config.paintWells.asIterable().associate { (color, wells) ->
                color to generateStirPaths(wells, config.paintStirStrokes, config.wellPadding)
            }
        else emptyMap()
        washPaths = if (config.requiresWash)
            generateStirPaths(
                config.washWells,
                config.washStirStrokes,
                config.wellPadding
            )
        else emptyList()
        refillCommands = generateRefillCommands(config.paintWells)
        washCommands = generateWashCommands(config.washWells)
    }

    /**
     * Generates wash well stir paths.
     */
    private fun generateStirPaths(
        wells: List<Rectangle>, strokes: Int, padding: Double
    ): List<List<Vector2>> = wells.map { generateStirPathPoints(it, strokes, padding) }

    /**
     * Generates the points of a stir path within a well.
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
     */
    private fun generateRefillCommands(
        wells: Map<ColorRGBa, List<Rectangle>>
    ): Map<ColorRGBa, List<WellCommand>> =
        wells.asIterable().associate { (color, wells) ->
            color to List(wells.size) { wellIndex ->
                generateWellPathCommand(
                    "refill_${config.palette[color]}_w${wellIndex}",
                    stirPaths[color]!![wellIndex]
                )
            }
        }

    /**
     * Generates wash commands for the given list of wells.
     */
    private fun generateWashCommands(
        wells: List<Rectangle>
    ): List<WellCommand> =
        List(wells.size) { wellIndex ->
            generateWellPathCommand("wash_w${wellIndex}", washPaths[wellIndex])
        }

    /**
     * Generates a well path command definition.
     */
    private fun generateWellPathCommand(
        cmdName: String, path: List<Vector2>
    ): WellCommand {
        val cmdDef = "draw_path ${roundAndStringify(path)}"
        return WellCommand(path.first(), cmdName, cmdDef)
    }

    private fun getNearestWellCmd(
        commands: List<WellCommand>,
        location: Vector2,
        type: String
    ): String =
        (commands.minByOrNull { location.distanceTo(it.location) }?.commandName
            ?: "# well not found for $type").plus("\n")

    fun getNearestWashWellCmd(location: Vector2): String =
        getNearestWellCmd(washCommands, location, "wash")

    fun getNearestPaintWellCmd(color: ColorRGBa, location: Vector2): String =
        getNearestWellCmd(
            refillCommands[color] ?: emptyList(), location, config.palette[color].toString()
        )

    /**
     * Builds a string containing all the command definitions for the output file.
     */
    fun cmdDefinitions(): String =
        (refillCommands.values.flatten() + washCommands).joinToString(separator = "\n") {
            "def ${it.commandName} ${it.command}"
        } + "\n"

}


/**
 * Saves the AxiDraw file set for the given composition and plot configuration.
 * The files include: the intermediate command file to drive the AxiDraw, an SVG of the
 * plot surface layout and the SVG file of the plot itself
 */
fun Composition.saveAxiDrawFileSet(baseFilename: String, config: PlotConfig) {
    val groupedSegments =
        groupSegmentsByLayerAndColor(this, config.displayScale, config.paperOffset)
    if (config.duplicateTolerance < Double.POSITIVE_INFINITY)
        removeDuplicateSegments(groupedSegments, config.duplicateTolerance)
    orderSegments(groupedSegments)

    val paths = generatePaths(groupedSegments, config)
    val refillData = RefillData(config)
    val plotData = generatePlotData(paths, refillData, config)

    writeAxiDrawFile(plotData, baseFilename)
    saveLayoutToSvgFile(baseFilename, refillData, config)
    savePlotToSvgFile(config.displayScale, baseFilename)
}

internal fun removeDuplicateSegments(groupedSegments: SegmentLayers, error: Double) {
    groupedSegments.forEach { (layerName, layer) ->
        layer.forEach { (color, segments) ->
            groupedSegments[layerName]?.set(color, deDuplicate(segments, error))
        }
    }
}

internal fun deDuplicate(segments: List<Segment2D>, error: Double): MutableList<Segment2D> {
    val sortedSegments = segments.sortedWith((compareByDescending { it.length }))
    val deDuped = mutableListOf<Segment2D>()
    sortedSegments.forEach { segment ->
        if (deDuped.none { other -> other.contains(segment, error) }) {
            deDuped.add(segment)
        } else {
            logger.info { "Duplicate segment $segment, error: $error" }
        }
    }
    return deDuped
}

internal fun Segment2D.contains(other: Segment2D, error: Double = 0.5): Boolean {
    if (isStraight() != other.isStraight()) return false
    val positions = if (isStraight()) {
        listOf(other.start, other.end)
    } else {
        listOf(other.start, other.end, other.position(3.0 / 10), other.position(6.0 / 10))
    }
    return this !== other && positions.all { this.on(it, error) != null }
}

private fun writeAxiDrawFile(data: String, baseFilename: String) {
    val axiDrawFile = File("${baseFilename}.txt")
    axiDrawFile.writeText(data)
}

/**
 * Saves the composition to an SVG file and, assuming the onscreen display dimensions are
 * based on paper size (i) it scales the SVG document to the desired paper
 * size (ii) scales the sketch to the paper size.
 */
fun Composition.savePlotToSvgFile(displayScale: Double, filename: String) {
    val origTransform = root.transform
    val origWidth = style.width
    val origHeight = style.height
    root.transform = transform { scale(CONVERSION_FACTOR / displayScale) }
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
 */
internal fun generatePlotData(
    pathLayers: PathLayers,
    refillData: RefillData,
    config: PlotConfig
): String {
    return buildString {
        append("${refillData.cmdDefinitions()}\n")
        append("${config.preOptions}\nupdate\n")

        append("penup\n")
        var location = Vector2.ZERO
        var currentColor = ColorRGBa.BLACK
        pathLayers.forEach { (layerName, layer) ->
            append("# Layer: ${layerName}\n")
            layer.forEach { (color, paths) ->
                if (paths.isNotEmpty()) {
                    if (config.toolType == DrawTool.Pen && currentColor != color) {
                        append("pause Color Change to ${config.palette[color]}\n")
                        currentColor = color
                    }
                    when {
                        config.isPainting ->
                            append(
                                writeStrokesAndRefills(paths, refillData, config, color, location)
                            )
                        config.requiresRefills ->
                            append(writePathsAndRefillPauses(paths, config))
                        else ->
                            paths.forEach {
                                append("draw_path ${roundAndStringify(it.points)}\n")
                            }
                    }
                    location = paths.last().points.last()
                }
            }
        }
        append("moveto 0 0\n")
    }
}

/**
 * Writes the strokes and refills for the given paths, config, color and lastLocation.
 */
private fun writeStrokesAndRefills(
    paths: MutableList<Path>,
    refillData: RefillData,
    config: PlotConfig,
    color: ColorRGBa,
    lastLocation: Vector2
): String {
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
        sb.append(refillData.getNearestPaintWellCmd(color, currentLocation))
        sb.append("draw_path ${roundAndStringify(stroke)}\n")
        currentLocation = stroke.last()
    }
    return sb.toString()
}

/**
 * Write paths and refill pauses for pen tool types.
 */
private fun writePathsAndRefillPauses(
    paths: List<Path>,
    config: PlotConfig
): String {
    var distanceSoFar = 0.0
    return buildString {
        paths.forEach { path ->
            if (distanceSoFar > config.refillDistance) {
                append("moveto 0 0\n")
                append("pause refill pen\n")
                distanceSoFar = 0.0
            }
            distanceSoFar += path.length()
            append("draw_path ${roundAndStringify(path.points)}\n")
        }
    }
}

/**
 * Groups segments of ShapeNodes by layer and color.
 */
internal fun groupSegmentsByLayerAndColor(
    composition: Composition,
    displayScale: Double,
    paperOffset: Vector2
): SegmentLayers {
    var currentLayer = DEFAULT_LAYER_NAME
    val segmentLayers: SegmentLayers = mutableMapOf(currentLayer to mutableMapOf())

    fun ensureLayerAndColorArePresent(layerName: String, color: ColorRGBa) {
        val layer = segmentLayers.getOrPut(layerName) { mutableMapOf() }
        layer.getOrPut(color) { mutableListOf() }
    }

    composition.root.visitAll {
        if (this is ShapeNode) {
            val currentColor = this.stroke ?: ColorRGBa.BLACK
            if (this.attributes.containsKey("layer")) {
                currentLayer = this.attributes["layer"] ?: currentLayer
            }
            ensureLayerAndColorArePresent(currentLayer, currentColor)
            segmentLayers[currentLayer]?.get(currentColor)!! +=
                this.shape.extractScaledSegments(displayScale, paperOffset)
        }
    }
    return segmentLayers
}

/**
 * Orders the segments within each layer / color group to minimize plotter travel.
 */
internal fun orderSegments(segmentLayers: SegmentLayers) {
    segmentLayers.forEach { (_, layer) ->
        layer.forEach { (color, segments) ->
            if (segments.isNotEmpty()) {
                val ordered = mutableListOf<Segment2D>()
                while (segments.isNotEmpty()) {
                    val lastPoint: Vector2 =
                        if (ordered.isEmpty()) Vector2.ZERO else ordered.last().end
                    val closestSegment = findClosestSegment(lastPoint, segments)
                    val isStartCloser =
                        lastPoint.squaredDistanceTo(closestSegment.start) < lastPoint.squaredDistanceTo(
                            closestSegment.end
                        )
                    if (isStartCloser) {
                        segments.remove(closestSegment)
                        ordered.add(closestSegment)
                    } else {
                        segments.remove(closestSegment)
                        ordered.add(closestSegment.reverse)
                    }
                }
                layer[color] = ordered
            }
        }
    }
}

private fun findClosestSegment(point: Vector2, segments: MutableList<Segment2D>): Segment2D {
    val closestToStart = segments.minByOrNull { it.start.squaredDistanceTo(point) }
    val closestToEnd = segments.minByOrNull { it.end.squaredDistanceTo(point) }
    return if (point.squaredDistanceTo(closestToStart!!.start)
        < point.squaredDistanceTo(closestToEnd!!.end)
    ) closestToStart else closestToEnd
}

/**
 * Generates equidistant points along a given segment based on a step resolution.
 * If the segment length is greater than the step resolution, the segment is divided into
 * equidistant positions and the points are returned. Otherwise, only the start and end points
 * of the segment are returned.
 */
private fun generateCurveSteps(
    segment: Segment2D, stepResolution: Double
): List<Vector2> {
    val numSteps: Int = ceil(segment.length / stepResolution).toInt()
    return if (numSteps > 1)
        segment.equidistantPositions(numSteps).toList()
    else
        listOf(segment.start, segment.end)
}

/**
 * Generates point paths from the layers of [Segment2D].
 * The distance between adjacent points on the created paths do not exceed
 * [PlotConfig.refillDistance].
 * Points from successive segments are joined into a single set of points
 * if the distance between them is less than [PlotConfig.pathTolerance]
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
                if (config.randomizeStart && path.closed()) path.rotatePoints()
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
            addPathContours(paths)
        }
        addPathContours(refillData.washPaths)
        rectangle(
            config.paperOffset.x,
            config.paperOffset.y,
            config.paperSize.height,
            config.paperSize.width
        )
    }
    layout.root.transform = transform { scale(CONVERSION_FACTOR) }
    layout.style.width = Length.Pixels.fromMillimeters(config.axiDrawTravel.x)
    layout.style.height = Length.Pixels.fromMillimeters(config.axiDrawTravel.y)
    layout.saveToFile(File("${filename}_Layout.svg"))
}

internal fun CompositionDrawer.addPathContours(paths: List<List<Vector2>>) {
    paths.forEach {
        contour(ShapeContour.fromPoints(it, closed = false))
    }
}

/**
 * Creates a composition representing the layout of palette wells defined
 * by [PlotConfig.paintWells] and [PlotConfig.washWells].
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
 */
internal fun roundAndStringify(points: List<Vector2>, decimals: Int = 3): String =
    points.map {
        "[${it.x.round(decimals)},${it.y.round(decimals)}]"
    }.toString().replace("\\s".toRegex(), "")

private fun Double.isApproximately(other: Double, tolerance: Double = 0.001): Boolean {
    return abs(this - other) <= tolerance
}
