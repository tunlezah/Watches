package io.github.tunlezah.watches.ui.photos

import io.github.tunlezah.watches.data.photos.PhotoFormat
import io.github.vinceglb.filekit.dialogs.FileKitType
import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoPickerTest {

    @Test fun aPhoneUsesTheSystemsPhotoPicker() {
        assertEquals(FileKitType.Image, photoPickerType(setOf(PhotoFormat.JPEG, PhotoFormat.HEIC), asFiles = false))
    }

    @Test fun aDesktopsFileDialogIsToldEveryFormatItReadsHeicIncluded() {
        val type = photoPickerType(setOf(PhotoFormat.JPEG, PhotoFormat.PNG, PhotoFormat.HEIC, PhotoFormat.WEBP), asFiles = true)
        assertEquals(FileKitType.File(setOf("jpg", "jpeg", "png", "heic", "heif", "webp")), type)
    }

    @Test fun aFormatTheDeviceCantReadIsntOffered() {
        val type = photoPickerType(setOf(PhotoFormat.JPEG, PhotoFormat.PNG), asFiles = true) as FileKitType.File
        assertEquals(setOf("jpg", "jpeg", "png"), type.extensions)
    }
}
