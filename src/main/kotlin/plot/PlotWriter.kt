package plot

import io.github.oshai.kotlinlogging.KotlinLogging
import org.openrndr.color.ColorRGBa
import org.openrndr.color.rgb
import org.openrndr.extra.composition.*
import org.openrndr.extra.noise.Random
import org.openrndr.extra.svg.saveToFile
import org.openrndr.math.Vector2
import org.openrndr.math.transforms.transform
import org.openrndr.panel.elements.round
import org.openrndr.shape.Rectangle
import org.openrndr.shape.Shape
import org.openrndr.shape.ShapeContour
import java.io.File
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.math.abs
import kotlin.math.ceil


val logger = KotlinLogging.logger { }

private const val DEFAULT_OPTIONS =
    "# SE/A3\n" +
    "options model 2\n" +
    "# brushless servo\n" +
    "#options penlift 3\n" +
    "# millimeter unit\n" +
    "options units 2\n" +
    "# max. safe pen up 67\n" +
    "options pen_pos_up 48\n" +
    "options pen_pos_down 33\n" +
    "options speed_pendown 10\n" +
    "options speed_penup 35\n"

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
 * @property bezierTolerance The margin of error
 * for what's considered a straight line when splitting a Bézier curve into linear segments.
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
    val pathTolerance: Double = 0.1524,
    val bezierTolerance: Double = 0.0004,
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

    fun eachColorHasAWell() = if (toolType == DrawTool.Dip || toolType == DrawTool.DipAndStir) {
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

internal typealias ContourColorGroups = MutableMap<ColorRGBa, MutableList<ShapeContour>>
internal typealias ContourLayers = MutableMap<String, ContourColorGroups>

data class WellCommand(val location: Vector2, val commandName: String, val command: String)

internal class RefillData(private val config: PlotConfig) {
    val stirPaths: Map<ColorRGBa, List<List<Vector2>>>
    val washPaths: List<List<Vector2>>
    private val refillCommands: Map<ColorRGBa, List<WellCommand>>
    private val washCommands: List<WellCommand>

    init {
        stirPaths =
            if (config.requiresStir)
                config.paintWells.asIterable().associate { (color, wells) ->
                    color to generateStirPaths(wells, config.paintStirStrokes, config.wellPadding)
                }
            else emptyMap()
        washPaths =
            if (config.requiresWash) generateStirPaths(
                config.washWells, config.washStirStrokes, config.wellPadding
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
    val groupedContours =
        groupContoursByLayerAndColor(this, config.displayScale, config.paperOffset)
    orderContours(groupedContours)

    val refillData = RefillData(config)
    val plotData = generatePlotData(groupedContours, refillData, config)

    writeAxiDrawFile(plotData, baseFilename)
    saveLayoutToSvgFile(baseFilename, refillData, config)
    savePlotToSvgFile(config.displayScale, baseFilename)
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
    contourLayers: ContourLayers, refillData: RefillData, config: PlotConfig
): String {
    return buildString {
        append("${refillData.cmdDefinitions()}\n")
        append("${config.preOptions}\nupdate\n")

        append("penup\n")
        var location = Vector2.ZERO
        var currentColor = ColorRGBa.BLACK
        contourLayers.forEach { (layerName, layer) ->
            append("# Layer: ${layerName}\n")
            layer.forEach { (color, contours) ->
                if (contours.isNotEmpty()) {
                    if (config.toolType == DrawTool.Pen && currentColor != color) {
                        append("pause Color Change to ${config.palette[color]}\n")
                        currentColor = color
                    }
                    val paths = mergePaths(contours.map { it.toPath(config) }, config.pathTolerance)

                    when {
                        config.isPainting -> append(
                            writeStrokesAndRefills(paths, refillData, config, color, location)
                        )

                        config.requiresRefills -> append(writePathsAndRefillPauses(paths, config))

                        else -> paths.forEach {
                            append("draw_path ${roundAndStringify(it)}\n")
                        }
                    }
                    location = contours.last().segments.last().end
                }
            }
        }
        append("moveto 0 0\n")
    }
}

/**
 * Merges the input paths based on a given tolerance value.
 *
 * @param paths The input paths to be merged. Each path is represented as a list of Vector2 points.
 * @param tolerance The maximum distance allowed between the end point of one path and the start point of the next path
 *                  in order for the paths to be merged.
 * @return The merged paths as a list of lists of Vector2 points.
 */
private fun mergePaths(paths: List<List<Vector2>>, tolerance: Double): List<List<Vector2>> {
    val mergedPaths = mutableListOf<MutableList<Vector2>>()
    var lastEndPoint = Vector2.ZERO
    paths.forEach { path ->
        if (mergedPaths.isEmpty()) {
            mergedPaths.add(path.toMutableList())
        } else {
            if (path.first().distanceTo(lastEndPoint) > tolerance) {
                mergedPaths.add(path.toMutableList())
            } else {
                mergedPaths.last() += path
            }
        }
        lastEndPoint = path.last()
    }
    return mergedPaths.toList()
}

/**
 * Converts the ShapeContour to a path represented as a list of Vector2 points.
 *
 * @param config The configuration settings for controlling the AxiDraw plotter.
 * @return The path represented as a list of Vector2 points.
 */
private fun ShapeContour.toPath(config: PlotConfig): List<Vector2> {
    val path = mutableListOf<Vector2>()
    this.segments.forEach { segment ->
        val points = when (segment.control.size) {
            1, 2 -> segment.adaptivePositions(config.bezierTolerance)
            else -> {
                if (segment.length <= config.refillDistance) {
                    listOf(segment.start, segment.end)
                } else {
                    val numStrokes = ceil(segment.length / config.refillDistance).toInt()
                    segment.equidistantPositions(numStrokes + 1)
                }
            }
        }
        if (path.isEmpty()) {
            path += points
        } else {
            if (path.last() == points.first()) path += points.drop(1)
            else logger.error { "None contiguous contour segments" }
        }
    }
    return if (config.randomizeStart) path.rotate() else path
}

/**
 * Rotate the points of the path by an offset if the path is closed.
 */
private fun List<Vector2>.rotate(): List<Vector2> {
    if (this.first() != this.last()) return this

    val offset = Random.int(0, this.lastIndex - 1)
    if (offset != 0) {
        val rotatedPoints = this.drop(offset) + this.slice(1..<offset)
        return rotatedPoints + rotatedPoints.first()
    }
    return this
}

/**
 * Writes the strokes and refills for the given paths, config, color and lastLocation.
 */
private fun writeStrokesAndRefills(
    paths: List<List<Vector2>>,
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
        strokePoints += path.first()
        var lastPoint = path.first()
        for (pointIndex in 1..path.lastIndex) {
            val currentPoint = path[pointIndex]
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
            if (pointIndex == path.lastIndex) {
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
private fun writePathsAndRefillPauses(paths: List<List<Vector2>>, config: PlotConfig): String {

    fun length(path: List<Vector2>) = path.zipWithNext { a, b -> a.distanceTo(b) }.sum()

    var distanceSoFar = 0.0
    return buildString {
        paths.forEach { path ->
            if (distanceSoFar > config.refillDistance) {
                append("moveto 0 0\n")
                append("pause refill pen\n")
                distanceSoFar = 0.0
            }
            distanceSoFar += length(path)
            append("draw_path ${roundAndStringify(path)}\n")
        }
    }
}


private fun getEffectiveColor(stroke: ColorRGBa?): ColorRGBa {
    val color = stroke ?: ColorRGBa.BLACK
    return if (color.alpha == 1.0) color else rgb(color.r, color.g, color.b)
}

/**
 * Groups contours of ShapeNodes by layer and color.
 */
internal fun groupContoursByLayerAndColor(
    composition: Composition, displayScale: Double, paperOffset: Vector2
): ContourLayers {
    var currentLayer = DEFAULT_LAYER_NAME
    val contourLayers: ContourLayers = mutableMapOf(currentLayer to mutableMapOf())

    fun ensureLayerAndColorArePresent(layerName: String, color: ColorRGBa) {
        val layer = contourLayers.getOrPut(layerName) { mutableMapOf() }
        layer.getOrPut(color) { mutableListOf() }
    }

    composition.root.visitAll {
        if (this is ShapeNode) {
            val currentColor = getEffectiveColor(this.stroke)

            if (this.attributes.containsKey("layer")) {
                currentLayer = this.attributes["layer"] ?: currentLayer
            }
            ensureLayerAndColorArePresent(currentLayer, currentColor)
            contourLayers[currentLayer]?.get(currentColor)!! += this.shape.transformContours(
                displayScale, paperOffset
            )
        }
    }
    return contourLayers
}

/**
 * Transform shape contours to paper size and position
 */
internal fun Shape.transformContours(
    displayScale: Double, paperPosition: Vector2
): List<ShapeContour> = this.transform(transform {
    translate(paperPosition)
    scale(1 / displayScale)
}).contours


/**
 * Orders the contours within each layer / color group to minimize plotter travel.
 */
internal fun orderContours(contourLayers: ContourLayers) {
    contourLayers.forEach { (_, layer) ->
        layer.forEach { (color, contours) ->
            if (contours.isNotEmpty()) {
                val ordered = mutableListOf<ShapeContour>()
                while (contours.isNotEmpty()) {
                    val lastPoint: Vector2 =
                        if (ordered.isEmpty()) Vector2.ZERO else ordered.last().segments.last().end
                    val closestContour = findClosestContour(lastPoint, contours)
                    val isStartCloser =
                        lastPoint.squaredDistanceTo(closestContour.segments.first().start) <= lastPoint.squaredDistanceTo(
                            closestContour.segments.last().end
                        )
                    contours.remove(closestContour)
                    if (isStartCloser) {
                        ordered.add(closestContour)
                    } else {
                        ordered.add(
                            ShapeContour.fromSegments(
                                closestContour.segments.asReversed().map { it.reverse },
                                closestContour.closed
                            )
                        )
                    }
                }
                layer[color] = ordered
            }
        }
    }
}

private fun findClosestContour(point: Vector2, contours: MutableList<ShapeContour>): ShapeContour {
    val closestToStart = contours.minByOrNull { it.segments.first().start.squaredDistanceTo(point) }
    val closestToEnd = contours.minByOrNull { it.segments.last().end.squaredDistanceTo(point) }
    return if (point.squaredDistanceTo(closestToStart!!.segments.first().start) <= point.squaredDistanceTo(
            closestToEnd!!.segments.last().end
        )
    ) closestToStart else closestToEnd
}


/**
 * Saves the layout of the plot to an SVG file.
 * The layout includes: The paint and wash wells,
 * the paint and wash stir paths and the paper boundary
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
