package com.example

import com.example.data.DemoCharacterCatalog
import com.example.model.CharacterCategory
import com.example.model.LiveFacePose
import com.example.model.NormalizedBox
import com.example.model.Point2D
import com.example.model.PuppetConfig
import com.example.tracking.OneEuroFilterBank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun builtInCatalog_containsSixRealisticThreeCartoonThreeAnimal() {
        val list = DemoCharacterCatalog.builtInCharacters
        assertEquals(12, list.size)
        assertEquals(6, list.count { it.category == CharacterCategory.REALISTIC })
        assertEquals(3, list.count { it.category == CharacterCategory.CARTOON })
        assertEquals(3, list.count { it.category == CharacterCategory.ANIMAL })
    }

    @Test
    fun puppetConfig_roundTripSerializationPreservesPointsAndBoxes() {
        val original = PuppetConfig(
            leftEye = Point2D(0.34f, 0.41f),
            rightEye = Point2D(0.66f, 0.41f),
            noseTip = Point2D(0.50f, 0.54f),
            mouthLeft = Point2D(0.38f, 0.69f),
            mouthRight = Point2D(0.62f, 0.69f),
            leftEyeBox = NormalizedBox(0.26f, 0.35f, 0.42f, 0.47f),
            rightEyeBox = NormalizedBox(0.58f, 0.35f, 0.74f, 0.47f),
            mouthBox = NormalizedBox(0.35f, 0.62f, 0.65f, 0.76f)
        )
        val encoded = original.serialize()
        val decoded = PuppetConfig.deserialize(encoded)
        assertEquals(original.leftEye.x, decoded.leftEye.x, 0.001f)
        assertEquals(original.rightEye.x, decoded.rightEye.x, 0.001f)
        assertEquals(original.noseTip.y, decoded.noseTip.y, 0.001f)
        assertEquals(original.mouthBox.bottom, decoded.mouthBox.bottom, 0.001f)
    }

    @Test
    fun oneEuroFilterBank_smoothsBlendshapesAndPoseWithoutNaN() {
        val bank = OneEuroFilterBank()
        val initial = LiveFacePose(jawOpen = 0.1f, eyeBlinkLeft = 0.0f, yawDegrees = 0f)
        val target = LiveFacePose(jawOpen = 0.8f, eyeBlinkLeft = 0.9f, yawDegrees = 15f)

        val f1 = bank.smoothPose(initial, smoothingStrength = 0.7f, timestampMs = 1000L)
        val f2 = bank.smoothPose(target, smoothingStrength = 0.7f, timestampMs = 1033L)

        assertTrue(f2.jawOpen > f1.jawOpen && f2.jawOpen < target.jawOpen)
        assertTrue(f2.eyeBlinkLeft > f1.eyeBlinkLeft && f2.eyeBlinkLeft < target.eyeBlinkLeft)
    }
}
