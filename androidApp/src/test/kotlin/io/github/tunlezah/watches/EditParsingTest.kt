package io.github.tunlezah.watches

import io.github.tunlezah.watches.core.model.Money
import io.github.tunlezah.watches.core.spec.SpecFields
import io.github.tunlezah.watches.core.spec.SpecValue
import io.github.tunlezah.watches.ui.edit.EditViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The edit form's text handling: what users type and what gets stored. */
class EditParsingTest {

    @Test fun decimalsAcceptCommaOrDot() {
        assertEquals(SpecValue.Number(39.5), EditViewModel.parse(SpecFields.Diameter, "39,5", "AUD"))
        assertEquals(SpecValue.Number(39.5), EditViewModel.parse(SpecFields.Diameter, " 39.5 ", "AUD"))
        assertNotNull(EditViewModel.validate(SpecFields.Diameter, "forty", "AUD"))
        assertNull(EditViewModel.validate(SpecFields.Diameter, "", "AUD"))
    }

    @Test fun yearsAreRangeChecked() {
        assertNull(EditViewModel.validate(SpecFields.YearFrom, "2021", "AUD"))
        assertNotNull(EditViewModel.validate(SpecFields.YearFrom, "21", "AUD"))
    }

    @Test fun pricesUseTheChosenCurrency() {
        assertEquals(SpecValue.Price(Money(1_095_050, "AUD")), EditViewModel.parse(SpecFields.PricePaid, "10,950.50", "AUD"))
        assertEquals(SpecValue.Price(Money(1_200_000, "JPY")), EditViewModel.parse(SpecFields.PricePaid, "1200000", "JPY"))
    }

    @Test fun textIsTrimmedAndBlankIsEmpty() {
        assertEquals(SpecValue.Text("Black Bay 58"), EditViewModel.parse(SpecFields.ModelName, "  Black Bay 58 ", "AUD"))
        assertNull(EditViewModel.parse(SpecFields.ModelName, "   ", "AUD"))
    }
}
