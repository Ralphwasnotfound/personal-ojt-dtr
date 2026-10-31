package com.lazarus.personalojtdtr.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLayoutTest {
    @Test fun `responsive launcher heights map to compact medium and large layouts`() {
        assertEquals(WidgetLayoutSize.COMPACT, widgetLayoutSize(148f))
        assertEquals(WidgetLayoutSize.MEDIUM, widgetLayoutSize(224f))
        assertEquals(WidgetLayoutSize.LARGE, widgetLayoutSize(460f))
        assertEquals(WidgetLayoutSize.MEDIUM, widgetLayoutSize(460f, 250f))
        assertEquals(WidgetLayoutSize.MEDIUM, widgetLayoutSize(280f))
    }
}
