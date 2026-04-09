package com.gedfix.models

import com.gedfix.db.DatabaseRepository

/**
 * Relationship between two people in the family tree.
 */
data class Relationship(
    val description: String,
    val path: List<String>,
    val commonAncestorXref: String?,
    val generationsUp: Int,
    val generationsDown: Int
)

/**
 * Calculates genealogical relationships between any two people in the tree
 * using bidirectional BFS to find the nearest common ancestor.
 */
object RelationshipCalculator {

    /**
     * Calculate the relationship between two people.
     * Returns null if no connection is found within a reasonable search depth.
     */
    fun calculate(
        personAXref: String,
        personBXref: String,
        db: DatabaseRepository,
        maxDepth: Int = 15
    ): Relationship? {
        if (personAXref == personBXref) {
            return Relationship(
                description = "Same person",
                path = listOf(personAXref),
                commonAncestorXref = personAXref,
                generationsUp = 0,
                generationsDown = 0
            )
        }

        // Build ancestor maps for both persons: xref -> (depth, path to that ancestor)
        val ancestorsA = buildAncestorPaths(personAXref, db, maxDepth)
        val ancestorsB = buildAncestorPaths(personBXref, db, maxDepth)

        // Find common ancestors and pick the one with shortest total path
        var bestAncestor: String? = null
        var bestGenUp = Int.MAX_VALUE
        var bestGenDown = Int.MAX_VALUE

        for ((ancestorXref, depthA) in ancestorsA) {
            val depthB = ancestorsB[ancestorXref]
            if (depthB != null) {
                val totalDist = depthA + depthB
                val currentBest = bestGenUp + bestGenDown
                if (totalDist < currentBest) {
                    bestAncestor = ancestorXref
                    bestGenUp = depthA
                    bestGenDown = depthB
                }
            }
        }

        if (bestAncestor == null) return null

        // Build the path from A -> common ancestor -> B
        val pathUp = buildPathToAncestor(personAXref, bestAncestor, db, maxDepth) ?: listOf(personAXref)
        val pathDown = buildPathToAncestor(personBXref, bestAncestor, db, maxDepth) ?: listOf(personBXref)

        val fullPath = pathUp + pathDown.reversed().drop(1) // drop common ancestor duplicate

        val description = relationshipName(bestGenUp, bestGenDown, personAXref, personBXref, db)

        return Relationship(
            description = description,
            path = fullPath,
            commonAncestorXref = bestAncestor,
            generationsUp = bestGenUp,
            generationsDown = bestGenDown
        )
    }

    /**
     * Build a map of all ancestors reachable from a person, with their depth.
     */
    private fun buildAncestorPaths(
        startXref: String,
        db: DatabaseRepository,
        maxDepth: Int
    ): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        val queue = ArrayDeque<Pair<String, Int>>() // (xref, depth)
        queue.add(startXref to 0)
        result[startXref] = 0

        while (queue.isNotEmpty()) {
            val (currentXref, depth) = queue.removeFirst()
            if (depth >= maxDepth) continue

            val (father, mother) = db.fetchParents(currentXref)
            if (father != null && father.xref !in result) {
                result[father.xref] = depth + 1
                queue.add(father.xref to depth + 1)
            }
            if (mother != null && mother.xref !in result) {
                result[mother.xref] = depth + 1
                queue.add(mother.xref to depth + 1)
            }
        }

        return result
    }

    /**
     * Build the path of xrefs from a person up to a specific ancestor.
     */
    private fun buildPathToAncestor(
        startXref: String,
        targetXref: String,
        db: DatabaseRepository,
        maxDepth: Int
    ): List<String>? {
        if (startXref == targetXref) return listOf(startXref)

        data class PathNode(val xref: String, val path: List<String>)

        val queue = ArrayDeque<PathNode>()
        val visited = mutableSetOf(startXref)
        queue.add(PathNode(startXref, listOf(startXref)))

        while (queue.isNotEmpty()) {
            val (currentXref, path) = queue.removeFirst()
            if (path.size > maxDepth + 1) continue

            val (father, mother) = db.fetchParents(currentXref)
            for (parent in listOfNotNull(father, mother)) {
                if (parent.xref == targetXref) {
                    return path + parent.xref
                }
                if (parent.xref !in visited) {
                    visited.add(parent.xref)
                    queue.add(PathNode(parent.xref, path + parent.xref))
                }
            }
        }
        return null
    }

    /**
     * Convert generation counts to a human-readable relationship name.
     * genUp = generations from person A up to the common ancestor.
     * genDown = generations from the common ancestor down to person B.
     */
    fun relationshipName(
        genUp: Int,
        genDown: Int,
        personAXref: String = "",
        personBXref: String = "",
        db: DatabaseRepository? = null
    ): String {
        // Determine sex of person B for gendered terms
        val personB = if (db != null && personBXref.isNotEmpty()) db.fetchPerson(personBXref) else null
        val isMale = personB?.sex == "M"
        val isFemale = personB?.sex == "F"

        return when {
            genUp == 0 && genDown == 0 -> "Same person"

            // Direct ancestor/descendant
            genUp == 0 && genDown == 1 -> when {
                isMale -> "Son"
                isFemale -> "Daughter"
                else -> "Child"
            }
            genUp == 1 && genDown == 0 -> when {
                isMale -> "Father"
                isFemale -> "Mother"
                else -> "Parent"
            }
            genUp == 0 && genDown == 2 -> when {
                isMale -> "Grandson"
                isFemale -> "Granddaughter"
                else -> "Grandchild"
            }
            genUp == 2 && genDown == 0 -> when {
                isMale -> "Grandfather"
                isFemale -> "Grandmother"
                else -> "Grandparent"
            }
            genUp == 0 && genDown > 2 -> {
                val prefix = greatPrefix(genDown - 2)
                when {
                    isMale -> "${prefix}Grandson"
                    isFemale -> "${prefix}Granddaughter"
                    else -> "${prefix}Grandchild"
                }
            }
            genUp > 2 && genDown == 0 -> {
                val prefix = greatPrefix(genUp - 2)
                when {
                    isMale -> "${prefix}Grandfather"
                    isFemale -> "${prefix}Grandmother"
                    else -> "${prefix}Grandparent"
                }
            }

            // Siblings
            genUp == 1 && genDown == 1 -> when {
                isMale -> "Brother"
                isFemale -> "Sister"
                else -> "Sibling"
            }

            // Uncle/Aunt and Nephew/Niece
            genUp == 2 && genDown == 1 -> when {
                isMale -> "Uncle"
                isFemale -> "Aunt"
                else -> "Uncle/Aunt"
            }
            genUp == 1 && genDown == 2 -> when {
                isMale -> "Nephew"
                isFemale -> "Niece"
                else -> "Nephew/Niece"
            }

            // Great uncle/aunt
            genUp > 2 && genDown == 1 -> {
                val prefix = greatPrefix(genUp - 2)
                when {
                    isMale -> "${prefix}Uncle"
                    isFemale -> "${prefix}Aunt"
                    else -> "${prefix}Uncle/Aunt"
                }
            }
            genUp == 1 && genDown > 2 -> {
                val prefix = greatPrefix(genDown - 2)
                when {
                    isMale -> "${prefix}Nephew"
                    isFemale -> "${prefix}Niece"
                    else -> "${prefix}Nephew/Niece"
                }
            }

            // Cousins
            genUp == genDown && genUp >= 2 -> {
                val cousinDegree = genUp - 1
                "${ordinal(cousinDegree)} Cousin"
            }

            // Cousins removed
            genUp >= 2 && genDown >= 2 -> {
                val minGen = minOf(genUp, genDown)
                val cousinDegree = minGen - 1
                val removed = kotlin.math.abs(genUp - genDown)
                "${ordinal(cousinDegree)} Cousin ${removedText(removed)}"
            }

            else -> "Related ($genUp up, $genDown down)"
        }
    }

    private fun greatPrefix(count: Int): String = when {
        count <= 0 -> ""
        count == 1 -> "Great-"
        count == 2 -> "2nd Great-"
        count == 3 -> "3rd Great-"
        else -> "${count}th Great-"
    }

    private fun ordinal(n: Int): String = when {
        n == 1 -> "1st"
        n == 2 -> "2nd"
        n == 3 -> "3rd"
        n in 11..13 -> "${n}th"
        n % 10 == 1 -> "${n}st"
        n % 10 == 2 -> "${n}nd"
        n % 10 == 3 -> "${n}rd"
        else -> "${n}th"
    }

    private fun removedText(removed: Int): String = when (removed) {
        1 -> "Once Removed"
        2 -> "Twice Removed"
        else -> "$removed Times Removed"
    }
}
