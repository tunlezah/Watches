package io.github.tunlezah.watches.data.photos

import io.github.tunlezah.watches.core.model.NormalizedRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** What a crop means in pixels, which every platform's codec then follows. */
class RenderPlanTest {

    @Test fun aWholePictureIsReducedToFitAndNeverEnlarged() {
        val big = RenderPlan.of(3000, 4000, Orientation.NORMAL, null, maxEdge = 2560)
        assertEquals(PixelRect(0, 0, 3000, 4000), big.region)
        assertEquals(1920 to 2560, big.outWidth to big.outHeight)
        val thumb = RenderPlan.of(3000, 4000, Orientation.NORMAL, null, maxEdge = 640)
        assertEquals(480 to 640, thumb.outWidth to thumb.outHeight)
        val small = RenderPlan.of(1200, 800, Orientation.NORMAL, null, maxEdge = 2560)
        assertEquals(1200 to 800, small.outWidth to small.outHeight)
    }

    @Test fun sizeAndCropAreInTheUprightPicture() {
        // A portrait photo, stored on its side as a phone's sensor reads it.
        val plan = RenderPlan.of(4000, 3000, Orientation.ROTATE_90, null, maxEdge = 640)
        assertEquals(3000 to 4000, plan.displayWidth to plan.displayHeight)
        assertEquals(480 to 640, plan.outWidth to plan.outHeight)
        // Its top left quarter, as a person sees it, is the stored grid's bottom left.
        val quarter = RenderPlan.of(4000, 3000, Orientation.ROTATE_90, NormalizedRect(0f, 0f, 0.5f, 0.5f), maxEdge = 640)
        assertEquals(PixelRect(0, 0, 1500, 2000), quarter.region)
        assertEquals(PixelRect(0, 1500, 2000, 3000), quarter.storedRegion)
    }

    @Test fun everyTurningKeepsEveryPixelOfTheRegion() {
        // A small grid with a different number in every stored pixel: cut a region both ways and compare.
        val w = 7
        val h = 5
        val crop = NormalizedRect(0.2f, 0.1f, 0.9f, 0.7f)
        for (orientation in Orientation.entries) {
            val plan = RenderPlan.of(w, h, orientation, crop, maxEdge = 100)
            // Where each stored pixel lands in the upright picture.
            val upright = HashMap<Pair<Int, Int>, Int>()
            for (y in 0 until h) for (x in 0 until w) upright[orientation.toDisplay(x, y, w, h)] = y * w + x
            assertEquals(w * h, upright.size, "$orientation: turning must not lose or double a pixel")
            val throughDisplay = buildSet {
                for (y in plan.region.top until plan.region.bottom) for (x in plan.region.left until plan.region.right) add(upright.getValue(x to y))
            }
            val throughStored = buildSet {
                for (y in plan.storedRegion.top until plan.storedRegion.bottom) for (x in plan.storedRegion.left until plan.storedRegion.right) add(y * w + x)
            }
            assertEquals(throughDisplay, throughStored, "$orientation: the stored region must be the same pixels")
            assertEquals(plan.region.width * plan.region.height, throughStored.size, orientation.name)
            assertEquals(plan.region.width to plan.region.height, plan.outWidth to plan.outHeight, "$orientation: small enough to keep as it is")
        }
    }

    @Test fun cropEdgesGoToTheNearestPixelAndARegionIsNeverEmpty() {
        val plan = RenderPlan.of(1000, 500, Orientation.NORMAL, NormalizedRect(0.2504f, 0.1f, 0.7496f, 0.9f), maxEdge = 4000)
        assertEquals(PixelRect(250, 50, 750, 450), plan.region)
        // A sliver thinner than a pixel still keeps one, inside the picture.
        val sliver = RenderPlan.of(100, 100, Orientation.NORMAL, NormalizedRect(0.999f, 0.5f, 1f, 0.5001f), maxEdge = 4000)
        assertEquals(PixelRect(99, 50, 100, 51), sliver.region)
        assertEquals(1 to 1, sliver.outWidth to sliver.outHeight)
    }

    @Test fun aVeryLongThinCropKeepsAtLeastOnePixelAcross() {
        val plan = RenderPlan.of(8000, 6000, Orientation.NORMAL, NormalizedRect(0f, 0.5f, 1f, 0.5005f), maxEdge = 640)
        assertEquals(8000, plan.region.width)
        assertEquals(3, plan.region.height)
        assertEquals(640 to 1, plan.outWidth to plan.outHeight)
    }

    @Test fun subsamplingNeverGoesBelowTheSizeToWrite() {
        // 8000 px wide down to 640: an eighth (1000) still has enough, a sixteenth (500) doesn't.
        assertEquals(8, RenderPlan.of(8000, 6000, Orientation.NORMAL, null, maxEdge = 640).sampleSize)
        assertEquals(1, RenderPlan.of(1200, 800, Orientation.NORMAL, null, maxEdge = 2560).sampleSize)
        assertEquals(2, RenderPlan.of(1280, 800, Orientation.NORMAL, null, maxEdge = 640).sampleSize)
        for (edge in listOf(100, 640, 2560)) {
            val plan = RenderPlan.of(4032, 3024, Orientation.ROTATE_270, NormalizedRect(0.1f, 0.2f, 0.8f, 0.9f), edge)
            assertTrue(maxOf(plan.region.width, plan.region.height) / plan.sampleSize >= maxOf(plan.outWidth, plan.outHeight), "edge $edge")
        }
    }

    @Test fun theSameNumbersWhateverThePlatform() {
        // Fixed expectations, so a change to the arithmetic can't pass unnoticed on one platform only.
        val plan = RenderPlan.of(4032, 3024, Orientation.ROTATE_90, NormalizedRect(0.123f, 0.456f, 0.789f, 0.987f), maxEdge = 2560)
        assertEquals(3024 to 4032, plan.displayWidth to plan.displayHeight)
        assertEquals(PixelRect(372, 1839, 2386, 3980), plan.region)
        assertEquals(PixelRect(1839, 638, 3980, 2652), plan.storedRegion)
        assertEquals(2014 to 2141, plan.outWidth to plan.outHeight)
        val thumb = RenderPlan.of(4032, 3024, Orientation.ROTATE_90, NormalizedRect(0.123f, 0.456f, 0.789f, 0.987f), maxEdge = 640)
        assertEquals(602 to 640, thumb.outWidth to thumb.outHeight)
    }

    @Test fun exifNumbersMapToTheEightTurnings() {
        assertEquals(Orientation.entries, (1..8).map { Orientation.fromExif(it) })
        assertEquals(Orientation.NORMAL, Orientation.fromExif(null))
        assertEquals(Orientation.NORMAL, Orientation.fromExif(0))
        assertEquals(Orientation.NORMAL, Orientation.fromExif(9))
        assertEquals(listOf(5, 6, 7, 8), Orientation.entries.filter { it.swapsAxes }.map { it.exif })
    }
}
