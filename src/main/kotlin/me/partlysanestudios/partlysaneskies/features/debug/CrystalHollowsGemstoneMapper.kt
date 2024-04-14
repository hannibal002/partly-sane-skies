package me.partlysanestudios.partlysaneskies.features.debug

import com.google.gson.GsonBuilder
import me.partlysanestudios.partlysaneskies.PartlySaneSkies.Companion.minecraft
import me.partlysanestudios.partlysaneskies.PartlySaneSkies.Companion.time
import me.partlysanestudios.partlysaneskies.utils.ChatUtils.sendClientMessage
import me.partlysanestudios.partlysaneskies.utils.MathUtils.round
import me.partlysanestudios.partlysaneskies.utils.StringUtils
import me.partlysanestudios.partlysaneskies.utils.StringUtils.formatNumber
import me.partlysanestudios.partlysaneskies.utils.geometry.vectors.Point3d
import me.partlysanestudios.partlysaneskies.utils.geometry.vectors.Range3d
import net.minecraft.block.material.Material
import net.minecraft.block.state.IBlockState
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.collections.ArrayList

object CrystalHollowsGemstoneMapper {

    private val range = Range3d(Point3d(201.0, 30.0, 201.0), Point3d(824.0, 189.0, 824.0))
    private val world = minecraft.theWorld
    private var alreadyCheckedCoords = ArrayList<Point3d>()

    fun scanWorld() {
        alreadyCheckedCoords = ArrayList()
        val rangeSize = (range.sortedPoints[1].x - range.sortedPoints[0].x) * (range.sortedPoints[1].y - range.sortedPoints[0].y) *  (range.sortedPoints[1].z - range.sortedPoints[0].z)
        var checkedBlocks = 0
        val gemstones = LinkedList<Gemstone>()
        val startTime = time

        for (x in range.sortedPoints[0].x.toInt()..range.sortedPoints[1].x.toInt()) {
            for (y in range.sortedPoints[0].y.toInt()..range.sortedPoints[1].y.toInt()) {
                for (z in range.sortedPoints[0].z.toInt()..range.sortedPoints[1].z.toInt()) {
                    checkedBlocks++
                    val timeElapsed = startTime - time
                    val estimatedTotalTime = (rangeSize * timeElapsed) / checkedBlocks
                    val timeLeft = (startTime + timeElapsed)
                    sendClientMessage("Checking block ($x, $y, $z) -- ${checkedBlocks.formatNumber()} / ${rangeSize.formatNumber()} (${(checkedBlocks/rangeSize *  100).round(1)}%)")
                    val point = Point3d(x.toDouble(), y.toDouble(), z.toDouble())

                    if (!isGlass(world.getBlockState(point.toBlockPosInt()))) {
                        continue
                    }

                    if (alreadyCheckedCoords.contains(point)) {
                        continue
                    }

                    val gemstoneCoords = ArrayList<Point3d>()

                    gemstoneCoords.add(point)
                    extractGemstone(point, gemstoneCoords)

                    gemstones.add(Gemstone(gemstoneCoords, world.getBlockState(point.toBlockPosInt()).block.localizedName))
                }
            }
        }
        sendClientMessage("Finished checking world, dumping data")
        dumpGemstoneData(gemstones)
        sendClientMessage("Data dumped data")
    }


    private fun dumpGemstoneData(gemstones: List<Gemstone>) {
        val json = GsonBuilder().setPrettyPrinting().create().toJson(gemstones)
        // Format the Instant to a human-readable date and time
        // Convert epoch time to LocalDateTime
        val dateTime = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault())

        // Format the LocalDateTime to a human-readable date and time
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
        val formattedDate = dateTime.format(formatter)

        File("./config/partly-sane-skies/dumps/").mkdirs()
        // Declares the file
        val file = File("./config/partly-sane-skies/dumps/gemstone-dump-${formattedDate}.json")
        file.createNewFile()
        file.setWritable(true)
        // Saves teh data to the file
        val writer = FileWriter(file)
        writer.write(json)
        writer.close()
    }

    private fun extractGemstone(point: Point3d, gemstoneCoords: ArrayList<Point3d>) {
        for (x in -1..1) {
            for (y in -1..1) {
                for (z in -1..1) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue
                    }

                    val newPoint = point + Point3d(x.toDouble(), y.toDouble(), z.toDouble())

                    if (alreadyCheckedCoords.contains(newPoint)) {
                        continue
                    }

                    if (gemstoneCoords.contains(newPoint)) {
                        continue
                    }

                    if (isGlass(world.getBlockState(newPoint.toBlockPosInt()))) {
                        gemstoneCoords.add(newPoint)
                        alreadyCheckedCoords.add(newPoint)
                        extractGemstone(newPoint, gemstoneCoords)
                    }
                }
            }
        }
    }

    private fun isGlass(blockState: IBlockState): Boolean {
        return blockState.block.material == Material.glass
    }


    private class Gemstone(val coordinates: List<Point3d>, val type: String) {
        val geographicMiddle: Point3d get() {
            var xPoints = 0.0
            var yPoints = 0.0
            var zPoints = 0.0


            for (point in coordinates) {
                xPoints += point.x
                yPoints += point.y
                zPoints += point.z
            }

            return Point3d(xPoints/coordinates.size, yPoints/coordinates.size, zPoints/coordinates.size)
        }

    }
}