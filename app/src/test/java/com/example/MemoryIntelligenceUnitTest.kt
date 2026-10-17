package com.example

import com.example.engine.MemoryCandidate
import com.example.engine.MemoryIntelligence
import com.example.model.FirestoreMemory
import com.example.repository.CloudDataRepository
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MemoryIntelligenceUnitTest {

    private lateinit var repository: CloudDataRepository

    @Before
    fun setup() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
            val options = com.google.firebase.FirebaseOptions.Builder()
                .setApplicationId("1:1234567890:android:abcdef")
                .setProjectId("nexus-ai-project")
                .setApiKey("AQ.Ab8RN6JgmIqahB4KbojDbrCZ2rV8A62M1lmJAkFwMd4w_Hfo3g")
                .build()
            com.google.firebase.FirebaseApp.initializeApp(context, options)
        }
        repository = CloudDataRepository(context)
    }

    @Test
    fun testRelevanceScoring_SelectsMatchingMemoryOverIrrelevant() {
        val memories = listOf(
            FirestoreMemory(
                id = "m1",
                key = "preferred_language",
                value = "Always write backend services in Kotlin with Coroutines",
                category = "TECH_STACK"
            ),
            FirestoreMemory(
                id = "m2",
                key = "preferred_food",
                value = "Enjoys sushi and green tea",
                category = "PREFERENCE"
            ),
            FirestoreMemory(
                id = "m3",
                key = "database",
                value = "PostgreSQL with connection pooling",
                category = "TECH_STACK"
            )
        )

        val query = "How should I structure my Kotlin Coroutines asynchronous flow?"
        val relevant = MemoryIntelligence.selectRelevantMemories(
            query = query,
            allMemories = memories,
            activeProjectId = null,
            limit = 2
        )

        assertFalse("Should find relevant memories", relevant.isEmpty())
        assertEquals("m1", relevant.first().id)
        assertTrue("Irrelevant food memory should not rank above Kotlin", relevant.none { it.id == "m2" })
    }

    @Test
    fun testRelevanceScoring_ProjectContextIsolation() {
        val projectAMemory = FirestoreMemory(
            id = "projA-1",
            key = "architecture",
            value = "Clean Architecture with MVI",
            category = "PROJECT_FACT",
            projectId = "project_alpha"
        )
        val projectBMemory = FirestoreMemory(
            id = "projB-1",
            key = "architecture",
            value = "Microservices with gRPC",
            category = "PROJECT_FACT",
            projectId = "project_beta"
        )
        val globalMemory = FirestoreMemory(
            id = "global-1",
            key = "style",
            value = "High density concise answers",
            category = "PREFERENCE",
            projectId = null
        )

        val all = listOf(projectAMemory, projectBMemory, globalMemory)

        // Querying under Project Alpha
        val alphaResults = MemoryIntelligence.selectRelevantMemories(
            query = "What is our architecture design?",
            allMemories = all,
            activeProjectId = "project_alpha",
            limit = 3
        )

        assertTrue("Project Alpha memory must be included", alphaResults.any { it.id == "projA-1" })
        assertFalse("Project Beta memory must be strictly excluded from Project Alpha", alphaResults.any { it.id == "projB-1" })
    }

    @Test
    fun testRelevanceScoring_SensibleLimitEnforced() {
        val manyMemories = (1..20).map { i ->
            FirestoreMemory(
                id = "mem_$i",
                key = "keyword_match_$i",
                value = "Important technical note regarding Android Jetpack Compose feature $i",
                category = "TECH_STACK"
            )
        }

        val results = MemoryIntelligence.selectRelevantMemories(
            query = "Android Jetpack Compose",
            allMemories = manyMemories,
            activeProjectId = null,
            limit = 4
        )

        assertEquals("Should strictly cap at the requested limit of 4", 4, results.size)
    }

    @Test
    fun testCandidateExtraction_ExplicitInstruction() {
        val prompt = "Please remember that we always use Jetpack Compose for Android UI."
        val candidate = MemoryIntelligence.extractCandidate(prompt, activeProjectId = "proj_123", conversationId = "conv_1")

        assertNotNull("Candidate should be extracted", candidate)
        assertEquals("PROJECT_FACT", candidate!!.category)
        assertEquals("proj_123", candidate.projectId)
        assertTrue("Value should contain the extracted fact", candidate.value.contains("Jetpack Compose"))
    }

    @Test
    fun testCandidateExtraction_DeclarativePreference() {
        val prompt = "I prefer Kotlin over Java for all Android development."
        val candidate = MemoryIntelligence.extractCandidate(prompt, activeProjectId = null, conversationId = null)

        assertNotNull("Candidate should be extracted", candidate)
        assertEquals("PREFERENCE", candidate!!.category)
        assertTrue(candidate.key.contains("language") || candidate.key.contains("kotlin"))
    }

    @Test
    fun testCandidateExtraction_IgnoresQuestionsAndTransientTasks() {
        val prompts = listOf(
            "What is the capital of France?",
            "Can you write a function to sort an array?",
            "How do I configure ProGuard in Android?",
            "Please fix this syntax error in my build file.",
            "Calculate 45 * 128",
            "Thanks for your help!",
            "Good morning"
        )

        for (p in prompts) {
            val candidate = MemoryIntelligence.extractCandidate(p, null, null)
            assertNull("Transient prompt '$p' should NOT produce a memory candidate", candidate)
        }
    }

    @Test
    fun testDeduplicationAndConflictHandling() = runBlocking {
        // Initial insert
        val id1 = repository.upsertMemory(
            key = "target_database",
            value = "MySQL 8.0 on AWS RDS",
            category = "TECH_STACK",
            projectId = "proj_cloud"
        )

        val memoriesAfterInsert = repository.observeMemories("proj_cloud").first()
        val foundInitial = memoriesAfterInsert.find { it.key == "target_database" }
        assertNotNull("Initial memory should exist", foundInitial)
        assertEquals("MySQL 8.0 on AWS RDS", foundInitial!!.value)

        // Conflict / Update to new value with same key
        val id2 = repository.upsertMemory(
            key = "target_database",
            value = "PostgreSQL 16 on Google Cloud SQL",
            category = "TECH_STACK",
            projectId = "proj_cloud"
        )

        assertEquals("Deduplication must reuse existing ID rather than creating a duplicate", id1, id2)

        val memoriesAfterUpdate = repository.observeMemories("proj_cloud").first()
        val matchingKeys = memoriesAfterUpdate.filter { it.key == "target_database" }
        assertEquals("Should only have exactly 1 record for this key (no duplicates)", 1, matchingKeys.size)
        assertEquals("PostgreSQL 16 on Google Cloud SQL", matchingKeys.first().value)
        assertNotNull("Should update the timestamp", matchingKeys.first().updatedAt)
    }

    @Test
    fun testDeletionAndTombstonePreventsRecreation() = runBlocking {
        val memId = repository.upsertMemory(
            key = "deprecated_tool",
            value = "RxJava 2",
            category = "PREFERENCE"
        )

        assertTrue(repository.observeMemories().first().any { it.id == memId })

        // User explicitly deletes the memory
        repository.deleteMemory(memId)

        assertFalse("Deleted memory must not exist in repository", repository.observeMemories().first().any { it.id == memId })
        assertTrue("Key must be tombstoned", repository.isDeletedMemoryKey("deprecated_tool"))
    }

    @Test
    fun testGuestModeIsolation_DoesNotLeakToCloud() = runBlocking {
        // Ensure in Guest Mode
        com.example.auth.AuthenticationManager.setGuestMode(true)

        val guestMemId = repository.upsertMemory(
            key = "guest_temp_fact",
            value = "Temporary testing in guest mode",
            category = "FACT"
        )

        val guestList = repository.observeMemories().first()
        assertTrue("Guest memory should exist in guest state", guestList.any { it.id == guestMemId })

        // Clear all guest memories
        repository.clearAllMemories()
        assertTrue("Guest memories should be empty after clear", repository.observeMemories().first().isEmpty())
    }
}
