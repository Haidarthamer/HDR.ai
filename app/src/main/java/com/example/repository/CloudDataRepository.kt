package com.example.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.auth.AuthenticationManager
import com.example.model.*
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CloudDataRepository private constructor(
    private val dbProvider: () -> FirebaseFirestore
) {

    constructor(context: Context) : this({
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    })

    // Defer Firebase initialization until a signed-in user actually needs cloud data.
    // This keeps the app usable in Guest Mode when google-services.json is not available.
    private val db: FirebaseFirestore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        dbProvider()
    }

    private val auth get() = try {
        Firebase.auth
    } catch (_: Throwable) {
        null
    }

    // Local in-memory store for Guest Mode
    private val guestConversations = MutableStateFlow<List<FirestoreConversation>>(emptyList())
    private val guestMessages = MutableStateFlow<Map<String, List<FirestoreMessage>>>(emptyMap())
    private val guestMemories = MutableStateFlow<List<FirestoreMemory>>(emptyList())
    private val guestProjects = MutableStateFlow<List<FirestoreProject>>(emptyList())
    private val guestArtifacts = MutableStateFlow<List<FirestoreArtifact>>(emptyList())
    private val guestAutomations = MutableStateFlow<List<FirestoreAutomation>>(emptyList())
    private val guestActivities = MutableStateFlow<List<FirestoreActivityItem>>(emptyList())
    private var guestPersonalization = UserPersonalization()

    val currentUserId: String?
        get() = auth?.currentUser?.uid ?: (if (AuthenticationManager.isGuestMode.value) "guest_local_user" else null)

    private fun isGuest(): Boolean = auth?.currentUser == null

    private fun requireUserId(): String {
        return auth?.currentUser?.uid ?: "guest_local_user"
    }

    // --- Conversations Stream ---
    fun observeConversations(projectIdFilter: String? = null): Flow<List<FirestoreConversation>> {
        if (isGuest()) {
            return guestConversations.map { list ->
                if (projectIdFilter != null) list.filter { it.projectId == projectIdFilter } else list
            }
        }

        val uid = auth?.currentUser?.uid ?: return guestConversations.map { list ->
            if (projectIdFilter != null) list.filter { it.projectId == projectIdFilter } else list
        }
        val ref: Query = db.collection("users").document(uid).collection("conversations")
            .orderBy("updatedAt", Query.Direction.DESCENDING)

        return ref.snapshots().map { snapshot ->
            val list = snapshot.toObjects(FirestoreConversation::class.java)
            if (projectIdFilter != null) {
                list.filter { it.projectId == projectIdFilter }
            } else {
                list
            }
        }.catch { e ->
            Log.e("CloudDataRepository", "Error observing conversations", e)
            emit(guestConversations.value)
        }
    }

    suspend fun createConversation(
        initialTitle: String = "New Chat",
        model: String = "gemini-3.5-flash",
        projectId: String? = null,
        mode: String = "NORMAL"
    ): String {
        val convId = UUID.randomUUID().toString()
        val uid = requireUserId()

        val conv = FirestoreConversation(
            id = convId,
            userId = uid,
            title = initialTitle,
            model = model,
            projectId = projectId ?: "",
            mode = mode,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now(),
            messageCount = 0
        )

        if (isGuest()) {
            guestConversations.value = listOf(conv) + guestConversations.value.filter { it.id != convId }
            return convId
        }

        try {
            val convRef = db.collection("users").document(uid).collection("conversations").document(convId)
            val payload = hashMapOf(
                "id" to convId,
                "userId" to uid,
                "title" to initialTitle,
                "model" to model,
                "projectId" to (projectId ?: ""),
                "mode" to mode,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "messageCount" to 0
            )
            convRef.set(payload).await()
        } catch (e: Exception) {
            Log.w("CloudDataRepository", "Saving conversation locally due to error: ${e.message}")
            guestConversations.value = listOf(conv) + guestConversations.value.filter { it.id != convId }
        }
        return convId
    }

    suspend fun updateConversationTitle(convId: String, newTitle: String) {
        if (isGuest()) {
            guestConversations.value = guestConversations.value.map {
                if (it.id == convId) it.copy(title = newTitle, updatedAt = Timestamp.now()) else it
            }
            return
        }
        val uid = auth?.currentUser?.uid ?: return
        try {
            db.collection("users").document(uid).collection("conversations").document(convId)
                .update(mapOf("title" to newTitle, "updatedAt" to FieldValue.serverTimestamp())).await()
        } catch (e: Exception) {
            guestConversations.value = guestConversations.value.map {
                if (it.id == convId) it.copy(title = newTitle, updatedAt = Timestamp.now()) else it
            }
        }
    }

    suspend fun moveConversationToProject(convId: String, projectId: String?) {
        if (isGuest()) {
            guestConversations.value = guestConversations.value.map {
                if (it.id == convId) it.copy(projectId = projectId ?: "", updatedAt = Timestamp.now()) else it
            }
            return
        }
        val uid = auth?.currentUser?.uid ?: return
        try {
            db.collection("users").document(uid).collection("conversations").document(convId)
                .update(mapOf("projectId" to (projectId ?: ""), "updatedAt" to FieldValue.serverTimestamp())).await()
        } catch (e: Exception) {
            Log.e("CloudDataRepository", "Error moving conversation", e)
        }
    }

    suspend fun deleteConversation(convId: String) {
        if (isGuest()) {
            guestConversations.value = guestConversations.value.filter { it.id != convId }
            guestMessages.value = guestMessages.value - convId
            return
        }
        val uid = auth?.currentUser?.uid ?: return
        try {
            val msgs = db.collection("users").document(uid).collection("conversations").document(convId).collection("messages").get().await()
            for (doc in msgs.documents) {
                doc.reference.delete().await()
            }
            db.collection("users").document(uid).collection("conversations").document(convId).delete().await()
        } catch (e: Exception) {
            guestConversations.value = guestConversations.value.filter { it.id != convId }
            guestMessages.value = guestMessages.value - convId
        }
    }

    // --- Messages Stream ---
    fun observeMessages(convId: String): Flow<List<FirestoreMessage>> {
        if (isGuest()) {
            return guestMessages.map { it[convId] ?: emptyList() }
        }

        val uid = auth?.currentUser?.uid ?: return guestMessages.map { it[convId] ?: emptyList() }
        val ref = db.collection("users").document(uid).collection("conversations").document(convId).collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)

        return ref.snapshots().map { snapshot ->
            snapshot.toObjects(FirestoreMessage::class.java)
        }.catch { e ->
            Log.e("CloudDataRepository", "Error observing messages for $convId", e)
            emit(guestMessages.value[convId] ?: emptyList())
        }
    }

    suspend fun saveMessage(
        convId: String,
        msgId: String = UUID.randomUUID().toString(),
        role: String,
        content: String,
        toolExecutions: List<Map<String, String>> = emptyList(),
        citations: List<Map<String, String>> = emptyList(),
        artifactId: String? = null
    ): String {
        val uid = requireUserId()
        val localMsg = FirestoreMessage(
            id = msgId,
            conversationId = convId,
            userId = uid,
            role = role,
            content = content,
            createdAt = Timestamp.now(),
            toolExecutions = toolExecutions,
            citations = citations,
            artifactId = artifactId ?: ""
        )

        val currentList = guestMessages.value[convId] ?: emptyList()
        guestMessages.value = guestMessages.value + (convId to (currentList + localMsg))

        if (isGuest()) {
            return msgId
        }

        try {
            val msgRef = db.collection("users").document(uid).collection("conversations").document(convId).collection("messages").document(msgId)
            val payload = hashMapOf(
                "id" to msgId,
                "conversationId" to convId,
                "userId" to uid,
                "role" to role,
                "content" to content,
                "createdAt" to FieldValue.serverTimestamp(),
                "toolExecutions" to toolExecutions,
                "citations" to citations,
                "artifactId" to (artifactId ?: "")
            )
            msgRef.set(payload).await()

            db.collection("users").document(uid).collection("conversations").document(convId).update(
                mapOf(
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "messageCount" to FieldValue.increment(1)
                )
            ).await()
        } catch (e: Exception) {
            Log.w("CloudDataRepository", "Failed saving message to Firestore: ${e.message}")
        }

        return msgId
    }

    // --- Cloud Long-Term Memory ---
    fun observeMemories(projectIdFilter: String? = null): Flow<List<FirestoreMemory>> {
        if (isGuest()) {
            return guestMemories.map { list ->
                if (projectIdFilter != null) list.filter { it.projectId == projectIdFilter || it.projectId.isNullOrEmpty() } else list
            }
        }
        val uid = auth?.currentUser?.uid ?: return guestMemories.map { list ->
            if (projectIdFilter != null) list.filter { it.projectId == projectIdFilter || it.projectId.isNullOrEmpty() } else list
        }
        return db.collection("users").document(uid).collection("memories")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots().map { snapshot ->
                val list = snapshot.toObjects(FirestoreMemory::class.java)
                if (projectIdFilter != null) {
                    list.filter { it.projectId == projectIdFilter || it.projectId.isNullOrEmpty() }
                } else {
                    list
                }
            }.catch { e ->
                Log.e("CloudDataRepository", "Error observing memories", e)
                emit(guestMemories.value)
            }
    }

    private val deletedMemoryKeys = mutableSetOf<String>()

    fun isDeletedMemoryKey(key: String): Boolean {
        return deletedMemoryKeys.contains(key.trim().lowercase())
    }

    suspend fun upsertMemory(
        key: String,
        value: String,
        category: String = "PREFERENCE",
        projectId: String? = null,
        sourceConversationId: String? = null
    ): String {
        val normalizedKey = key.trim().lowercase()
        val uid = requireUserId()
        val currentList = if (isGuest()) guestMemories.value else {
            val uidAuth = auth?.currentUser?.uid
            if (uidAuth != null) {
                try {
                    val snap = db.collection("users").document(uidAuth).collection("memories").get().await()
                    snap.toObjects(FirestoreMemory::class.java)
                } catch (e: Exception) {
                    guestMemories.value
                }
            } else guestMemories.value
        }

        // Deduplication & Conflict check: match normalized key and project alignment
        val existing = currentList.find {
            it.key.trim().lowercase() == normalizedKey &&
            (it.projectId == projectId || (it.projectId.isNullOrEmpty() && projectId.isNullOrEmpty()))
        }

        val memId = existing?.id ?: UUID.randomUUID().toString()
        val now = Timestamp.now()
        val updatedMem = FirestoreMemory(
            id = memId,
            userId = uid,
            key = key.trim(),
            value = value.trim(),
            category = category,
            projectId = projectId ?: "",
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            sourceConversationId = sourceConversationId ?: existing?.sourceConversationId
        )

        // Clear tombstone if user explicitly saves or updates it
        deletedMemoryKeys.remove(normalizedKey)

        if (isGuest()) {
            guestMemories.value = if (existing != null) {
                guestMemories.value.map { if (it.id == memId) updatedMem else it }
            } else {
                listOf(updatedMem) + guestMemories.value
            }
            return memId
        }

        try {
            val memRef = db.collection("users").document(uid).collection("memories").document(memId)
            val payload = hashMapOf(
                "id" to memId,
                "userId" to uid,
                "key" to key.trim(),
                "value" to value.trim(),
                "category" to category,
                "projectId" to (projectId ?: ""),
                "createdAt" to (existing?.createdAt ?: FieldValue.serverTimestamp()),
                "updatedAt" to FieldValue.serverTimestamp(),
                "sourceConversationId" to (sourceConversationId ?: existing?.sourceConversationId ?: "")
            )
            memRef.set(payload).await()
        } catch (e: Exception) {
            Log.w("CloudDataRepository", "Error upserting memory to Firestore: ${e.message}")
        }
        return memId
    }

    suspend fun saveMemory(key: String, value: String, category: String = "PREFERENCE", projectId: String? = null) {
        upsertMemory(key = key, value = value, category = category, projectId = projectId)
    }

    suspend fun updateMemory(memId: String, key: String, value: String, category: String = "PREFERENCE", projectId: String? = null) {
        val uid = requireUserId()
        val existing = guestMemories.value.find { it.id == memId }
        val updatedMem = FirestoreMemory(
            id = memId,
            userId = uid,
            key = key.trim(),
            value = value.trim(),
            category = category,
            projectId = projectId ?: existing?.projectId ?: "",
            createdAt = existing?.createdAt ?: Timestamp.now(),
            updatedAt = Timestamp.now(),
            sourceConversationId = existing?.sourceConversationId
        )

        if (isGuest()) {
            guestMemories.value = guestMemories.value.map { if (it.id == memId) updatedMem else it }
            return
        }

        val authUid = auth?.currentUser?.uid ?: return
        try {
            val memRef = db.collection("users").document(authUid).collection("memories").document(memId)
            memRef.update(
                mapOf(
                    "key" to key.trim(),
                    "value" to value.trim(),
                    "category" to category,
                    "projectId" to (projectId ?: existing?.projectId ?: ""),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: Exception) {
            Log.w("CloudDataRepository", "Error updating memory: ${e.message}")
        }
    }

    suspend fun deleteMemory(memId: String) {
        val target = guestMemories.value.find { it.id == memId }
        if (target != null) {
            deletedMemoryKeys.add(target.key.trim().lowercase())
        }
        guestMemories.value = guestMemories.value.filter { it.id != memId }
        if (isGuest()) return
        val uid = auth?.currentUser?.uid ?: return
        try {
            val memDoc = db.collection("users").document(uid).collection("memories").document(memId).get().await()
            val memKey = memDoc.getString("key")
            if (memKey != null) {
                deletedMemoryKeys.add(memKey.trim().lowercase())
            }
            db.collection("users").document(uid).collection("memories").document(memId).delete().await()
        } catch (e: Exception) {
            Log.e("CloudDataRepository", "Error deleting memory", e)
        }
    }

    suspend fun clearAllMemories() {
        for (m in guestMemories.value) {
            deletedMemoryKeys.add(m.key.trim().lowercase())
        }
        guestMemories.value = emptyList()
        if (isGuest()) return
        val uid = auth?.currentUser?.uid ?: return
        try {
            val snapshot = db.collection("users").document(uid).collection("memories").get().await()
            for (doc in snapshot.documents) {
                val memKey = doc.getString("key")
                if (memKey != null) deletedMemoryKeys.add(memKey.trim().lowercase())
                doc.reference.delete().await()
            }
        } catch (e: Exception) {
            Log.e("CloudDataRepository", "Error clearing memories", e)
        }
    }

    // --- Projects Workspace System ---
    fun observeProjects(): Flow<List<FirestoreProject>> {
        if (isGuest()) return guestProjects
        val uid = auth?.currentUser?.uid ?: return guestProjects
        return db.collection("users").document(uid).collection("projects")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots().map { snapshot ->
                snapshot.toObjects(FirestoreProject::class.java)
            }.catch { e ->
                Log.e("CloudDataRepository", "Error observing projects", e)
                emit(guestProjects.value)
            }
    }

    suspend fun createProject(name: String, description: String, customInstructions: String, enabledPlugins: List<String>): String {
        val uid = requireUserId()
        val projId = UUID.randomUUID().toString()
        val localProj = FirestoreProject(
            id = projId,
            userId = uid,
            name = name,
            description = description,
            customInstructions = customInstructions,
            enabledPlugins = enabledPlugins,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        guestProjects.value = listOf(localProj) + guestProjects.value
        recordActivity("Created Project", "Project: $name", "TOOL")

        if (isGuest()) return projId

        try {
            val ref = db.collection("users").document(uid).collection("projects").document(projId)
            val payload = hashMapOf(
                "id" to projId,
                "userId" to uid,
                "name" to name,
                "description" to description,
                "customInstructions" to customInstructions,
                "enabledPlugins" to enabledPlugins,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            ref.set(payload).await()
        } catch (e: Exception) {
            Log.w("CloudDataRepository", "Error saving project to cloud", e)
        }
        return projId
    }

    suspend fun updateProject(project: FirestoreProject) {
        guestProjects.value = guestProjects.value.map { if (it.id == project.id) project else it }
        if (isGuest()) return
        val uid = auth?.currentUser?.uid ?: return
        try {
            val ref = db.collection("users").document(uid).collection("projects").document(project.id)
            val payload = hashMapOf(
                "name" to project.name,
                "description" to project.description,
                "customInstructions" to project.customInstructions,
                "enabledPlugins" to project.enabledPlugins,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            ref.update(payload as Map<String, Any>).await()
        } catch (e: Exception) {
            Log.e("CloudDataRepository", "Error updating project", e)
        }
    }

    suspend fun deleteProject(projectId: String) {
        guestProjects.value = guestProjects.value.filter { it.id != projectId }
        if (isGuest()) return
        val uid = auth?.currentUser?.uid ?: return
        try {
            db.collection("users").document(uid).collection("projects").document(projectId).delete().await()
        } catch (e: Exception) {
            Log.e("CloudDataRepository", "Error deleting project", e)
        }
    }

    // --- Artifacts System ---
    fun observeArtifacts(): Flow<List<FirestoreArtifact>> {
        if (isGuest()) return guestArtifacts
        val uid = auth?.currentUser?.uid ?: return guestArtifacts
        return db.collection("users").document(uid).collection("artifacts")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots().map { snapshot ->
                snapshot.toObjects(FirestoreArtifact::class.java)
            }.catch { e ->
                Log.e("CloudDataRepository", "Error observing artifacts", e)
                emit(guestArtifacts.value)
            }
    }

    suspend fun saveArtifact(
        title: String,
        type: String,
        content: String,
        language: String = "kotlin",
        artifactId: String = UUID.randomUUID().toString()
    ): String {
        val uid = requireUserId()
        val localArt = FirestoreArtifact(
            id = artifactId,
            userId = uid,
            title = title,
            type = type,
            content = content,
            language = language,
            version = 1,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        val existingIndex = guestArtifacts.value.indexOfFirst { it.id == artifactId }
        guestArtifacts.value = if (existingIndex >= 0) {
            val existing = guestArtifacts.value[existingIndex]
            guestArtifacts.value.toMutableList().apply {
                this[existingIndex] = existing.copy(
                    content = content,
                    version = existing.version + 1,
                    versions = listOf(existing.content) + existing.versions,
                    updatedAt = Timestamp.now()
                )
            }
        } else {
            listOf(localArt) + guestArtifacts.value
        }

        recordActivity("Generated Artifact", "$title ($type)", "TOOL")

        if (isGuest()) return artifactId

        try {
            val ref = db.collection("users").document(uid).collection("artifacts").document(artifactId)
            val existing = ref.get().await()

            if (existing.exists()) {
                val prevContent = existing.getString("content") ?: ""
                val prevVersions = (existing.get("versions") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                val newVersions = (listOf(prevContent) + prevVersions).take(10)
                val versionCount = (existing.getLong("version") ?: 1L).toInt() + 1

                ref.update(
                    mapOf(
                        "title" to title,
                        "content" to content,
                        "language" to language,
                        "version" to versionCount,
                        "versions" to newVersions,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val payload = hashMapOf(
                    "id" to artifactId,
                    "userId" to uid,
                    "title" to title,
                    "type" to type,
                    "content" to content,
                    "language" to language,
                    "version" to 1,
                    "versions" to emptyList<String>(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                ref.set(payload).await()
            }
        } catch (e: Exception) {
            Log.w("CloudDataRepository", "Error saving artifact to cloud", e)
        }
        return artifactId
    }

    suspend fun deleteArtifact(artifactId: String) {
        guestArtifacts.value = guestArtifacts.value.filter { it.id != artifactId }
        if (isGuest()) return
        val uid = auth?.currentUser?.uid ?: return
        try {
            db.collection("users").document(uid).collection("artifacts").document(artifactId).delete().await()
        } catch (e: Exception) {
            Log.e("CloudDataRepository", "Error deleting artifact", e)
        }
    }

    // --- Automations System ---
    fun observeAutomations(): Flow<List<FirestoreAutomation>> {
        if (isGuest()) return guestAutomations
        val uid = auth?.currentUser?.uid ?: return guestAutomations
        return db.collection("users").document(uid).collection("automations")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots().map { snapshot ->
                snapshot.toObjects(FirestoreAutomation::class.java)
            }.catch { e ->
                Log.e("CloudDataRepository", "Error observing automations", e)
                emit(guestAutomations.value)
            }
    }

    suspend fun createAutomation(name: String, prompt: String, schedule: String, allowedPlugins: List<String>): String {
        val uid = requireUserId()
        val autoId = UUID.randomUUID().toString()
        val localAuto = FirestoreAutomation(
            id = autoId,
            userId = uid,
            name = name,
            prompt = prompt,
            schedule = schedule,
            enabled = true,
            allowedPlugins = allowedPlugins,
            lastStatus = "Scheduled",
            executionHistory = listOf(
                mapOf("timestamp" to System.currentTimeMillis().toString(), "status" to "Created automation")
            ),
            createdAt = Timestamp.now()
        )
        guestAutomations.value = listOf(localAuto) + guestAutomations.value
        recordActivity("Created Automation", name, "AUTOMATION")

        if (isGuest()) return autoId

        try {
            val ref = db.collection("users").document(uid).collection("automations").document(autoId)
            val payload = hashMapOf(
                "id" to autoId,
                "userId" to uid,
                "name" to name,
                "prompt" to prompt,
                "schedule" to schedule,
                "enabled" to true,
                "allowedPlugins" to allowedPlugins,
                "lastStatus" to "Scheduled",
                "executionHistory" to listOf(
                    mapOf("timestamp" to System.currentTimeMillis().toString(), "status" to "Created automation")
                ),
                "createdAt" to FieldValue.serverTimestamp()
            )
            ref.set(payload).await()
        } catch (e: Exception) {
            Log.w("CloudDataRepository", "Error saving automation to cloud", e)
        }
        return autoId
    }

    suspend fun toggleAutomation(autoId: String, enabled: Boolean) {
        guestAutomations.value = guestAutomations.value.map { if (it.id == autoId) it.copy(enabled = enabled) else it }
        if (isGuest()) return
        val uid = auth?.currentUser?.uid ?: return
        try {
            db.collection("users").document(uid).collection("automations").document(autoId)
                .update("enabled", enabled).await()
        } catch (e: Exception) {
            Log.e("CloudDataRepository", "Error toggling automation", e)
        }
    }

    suspend fun runAutomationNow(autoId: String, name: String, resultSummary: String) {
        recordActivity("Executed Automation", name, "AUTOMATION")
        if (isGuest()) return
        val uid = auth?.currentUser?.uid ?: return
        try {
            val ref = db.collection("users").document(uid).collection("automations").document(autoId)
            val historyItem = mapOf(
                "timestamp" to System.currentTimeMillis().toString(),
                "status" to "Completed: $resultSummary"
            )
            ref.update(
                mapOf(
                    "lastRun" to FieldValue.serverTimestamp(),
                    "lastStatus" to "Success",
                    "executionHistory" to FieldValue.arrayUnion(historyItem)
                )
            ).await()
        } catch (e: Exception) {
            Log.e("CloudDataRepository", "Error running automation", e)
        }
    }

    suspend fun deleteAutomation(autoId: String) {
        guestAutomations.value = guestAutomations.value.filter { it.id != autoId }
        if (isGuest()) return
        val uid = auth?.currentUser?.uid ?: return
        try {
            db.collection("users").document(uid).collection("automations").document(autoId).delete().await()
        } catch (e: Exception) {
            Log.e("CloudDataRepository", "Error deleting automation", e)
        }
    }

    // --- Activities Center ---
    fun observeActivities(): Flow<List<FirestoreActivityItem>> {
        if (isGuest()) return guestActivities
        val uid = auth?.currentUser?.uid ?: return guestActivities
        return db.collection("users").document(uid).collection("activities")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .snapshots().map { snapshot ->
                snapshot.toObjects(FirestoreActivityItem::class.java).take(50)
            }.catch { e ->
                Log.e("CloudDataRepository", "Error observing activities", e)
                emit(guestActivities.value)
            }
    }

    suspend fun recordActivity(title: String, details: String, category: String, status: String = "SUCCESS") {
        val uid = currentUserId ?: return
        val id = UUID.randomUUID().toString()
        val localAct = FirestoreActivityItem(
            id = id,
            userId = uid,
            title = title,
            details = details,
            category = category,
            status = status,
            timestamp = Timestamp.now()
        )
        guestActivities.value = (listOf(localAct) + guestActivities.value).take(50)

        if (isGuest()) return

        try {
            val ref = db.collection("users").document(uid).collection("activities").document(id)
            val payload = hashMapOf(
                "id" to id,
                "userId" to uid,
                "title" to title,
                "details" to details,
                "category" to category,
                "status" to status,
                "timestamp" to FieldValue.serverTimestamp()
            )
            ref.set(payload).await()
        } catch (e: Exception) {
            Log.w("CloudDataRepository", "Error recording activity to cloud", e)
        }
    }

    // --- Personalization Settings ---
    suspend fun savePersonalization(settings: UserPersonalization) {
        guestPersonalization = settings
        if (isGuest()) return
        val uid = auth?.currentUser?.uid ?: return
        try {
            val ref = db.collection("users").document(uid).collection("settings").document("personalization")
            val payload = hashMapOf(
                "assistantName" to settings.assistantName,
                "personality" to settings.personality,
                "responseStyle" to settings.responseStyle,
                "preferredLanguage" to settings.preferredLanguage,
                "customInstructions" to settings.customInstructions,
                "memoryEnabled" to settings.memoryEnabled,
                "notificationsEnabled" to settings.notificationsEnabled,
                "contextCompressionEnabled" to settings.contextCompressionEnabled
            )
            ref.set(payload).await()
        } catch (e: Exception) {
            Log.w("CloudDataRepository", "Error saving personalization", e)
        }
    }

    suspend fun loadPersonalization(): UserPersonalization {
        if (isGuest()) return guestPersonalization
        val uid = auth?.currentUser?.uid ?: return guestPersonalization
        return try {
            val doc = db.collection("users").document(uid).collection("settings").document("personalization").get().await()
            if (doc.exists()) {
                UserPersonalization(
                    assistantName = doc.getString("assistantName") ?: "NexusAI",
                    personality = doc.getString("personality") ?: "Helpful, concise, and expert",
                    responseStyle = doc.getString("responseStyle") ?: "Concise & Structured",
                    preferredLanguage = doc.getString("preferredLanguage") ?: "English",
                    customInstructions = doc.getString("customInstructions") ?: "",
                    memoryEnabled = doc.getBoolean("memoryEnabled") ?: true,
                    notificationsEnabled = doc.getBoolean("notificationsEnabled") ?: true,
                    contextCompressionEnabled = doc.getBoolean("contextCompressionEnabled") ?: true
                )
            } else {
                guestPersonalization
            }
        } catch (e: Exception) {
            guestPersonalization
        }
    }
}
