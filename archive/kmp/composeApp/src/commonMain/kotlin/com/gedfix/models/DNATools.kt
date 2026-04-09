package com.gedfix.models

/**
 * DNA relationship prediction from shared centiMorgans.
 * Based on the Shared cM Project v4 (Blaine Bettinger) data.
 */
data class DNARelationshipPrediction(
    val relationship: String,
    val averageCM: Double,
    val minCM: Double,
    val maxCM: Double,
    val probability: String
)

object DNACalculator {

    /**
     * Known relationship ranges from the Shared cM Project v4.
     * Format: relationship label, average cM, min cM, max cM
     */
    private val RELATIONSHIP_TABLE = listOf(
        RelationshipRange("Identical Twin", 3400.0, 3300.0, 3500.0),
        RelationshipRange("Parent/Child", 3485.0, 3330.0, 3720.0),
        RelationshipRange("Full Sibling", 2613.0, 2209.0, 3384.0),
        RelationshipRange("Grandparent/Grandchild", 1754.0, 1156.0, 2311.0),
        RelationshipRange("Aunt/Uncle", 1741.0, 1201.0, 2282.0),
        RelationshipRange("Half Sibling", 1759.0, 1160.0, 2436.0),
        RelationshipRange("Niece/Nephew", 1741.0, 1201.0, 2282.0),
        RelationshipRange("Great-Grandparent", 881.0, 492.0, 1315.0),
        RelationshipRange("Great-Aunt/Uncle", 851.0, 492.0, 1315.0),
        RelationshipRange("1st Cousin", 866.0, 553.0, 1225.0),
        RelationshipRange("Half Aunt/Uncle", 851.0, 492.0, 1315.0),
        RelationshipRange("1st Cousin Once Removed", 433.0, 220.0, 680.0),
        RelationshipRange("Great-Great-Grandparent", 444.0, 176.0, 712.0),
        RelationshipRange("Half 1st Cousin", 433.0, 156.0, 550.0),
        RelationshipRange("2nd Cousin", 229.0, 41.0, 502.0),
        RelationshipRange("2nd Cousin Once Removed", 122.0, 18.0, 338.0),
        RelationshipRange("3rd Cousin", 73.0, 0.0, 234.0),
        RelationshipRange("3rd Cousin Once Removed", 48.0, 0.0, 173.0),
        RelationshipRange("4th Cousin", 35.0, 0.0, 139.0),
        RelationshipRange("4th Cousin Once Removed", 20.0, 0.0, 99.0),
        RelationshipRange("5th Cousin", 15.0, 0.0, 75.0),
        RelationshipRange("6th Cousin", 10.0, 0.0, 53.0),
    )

    /**
     * Given shared centiMorgans, predict possible relationships.
     * Returns matches sorted by probability (closest average first).
     */
    fun predictRelationships(sharedCM: Double): List<DNARelationshipPrediction> {
        if (sharedCM <= 0) return emptyList()

        return RELATIONSHIP_TABLE
            .filter { sharedCM in it.minCM..it.maxCM }
            .sortedBy { kotlin.math.abs(it.averageCM - sharedCM) }
            .map { range ->
                val distance = kotlin.math.abs(range.averageCM - sharedCM)
                val span = range.maxCM - range.minCM
                val closeness = if (span > 0) (1.0 - (distance / span)).coerceIn(0.0, 1.0) else 1.0
                val prob = when {
                    closeness > 0.8 -> "Very likely"
                    closeness > 0.5 -> "Likely"
                    closeness > 0.3 -> "Possible"
                    else -> "Less likely"
                }
                DNARelationshipPrediction(
                    relationship = range.label,
                    averageCM = range.averageCM,
                    minCM = range.minCM,
                    maxCM = range.maxCM,
                    probability = prob
                )
            }
    }

    /**
     * Calculate expected shared cM for a known relationship.
     */
    fun expectedCMForRelationship(relationship: String): RelationshipRange? {
        return RELATIONSHIP_TABLE.firstOrNull {
            it.label.equals(relationship, ignoreCase = true)
        }
    }

    /**
     * Get all known relationship types for the lookup table.
     */
    fun allRelationships(): List<RelationshipRange> = RELATIONSHIP_TABLE

    data class RelationshipRange(
        val label: String,
        val averageCM: Double,
        val minCM: Double,
        val maxCM: Double
    )
}
