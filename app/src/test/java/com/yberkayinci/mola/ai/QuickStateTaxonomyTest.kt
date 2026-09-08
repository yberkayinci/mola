package com.yberkayinci.mola.ai

import com.yberkayinci.mola.data.QuickStateTaxonomy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickStateTaxonomyTest {
    @Test
    fun canonicalIdsPassThrough() {
        QuickStateTaxonomy.CANONICAL_IDS.forEach { id ->
            assertEquals(id, QuickStateTaxonomy.canonicalize(id))
        }
    }

    @Test
    fun recognizableAliasesMapToCanonicalIds() {
        assertEquals("tired", QuickStateTaxonomy.canonicalize("low_energy"))
        assertEquals("procrastinating", QuickStateTaxonomy.canonicalize("Procrastination"))
        assertEquals("relaxing", QuickStateTaxonomy.canonicalize("intentional-rest"))
        assertEquals("low_motivation", QuickStateTaxonomy.canonicalize("unmotivated"))
        assertEquals("overwhelmed", QuickStateTaxonomy.canonicalize("burnout"))
        assertEquals("habit", QuickStateTaxonomy.canonicalize("automatic"))
        assertEquals("late_night", QuickStateTaxonomy.canonicalize("Night"))
    }

    @Test
    fun inventedIdsAreDiscarded() {
        assertNull(QuickStateTaxonomy.canonicalize("zombie_mode"))
        assertNull(QuickStateTaxonomy.canonicalize("doomscrolling"))
        assertNull(QuickStateTaxonomy.canonicalize(""))
    }
}
