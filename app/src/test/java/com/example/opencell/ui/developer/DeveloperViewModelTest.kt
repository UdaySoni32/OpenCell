package com.example.opencell.ui.developer

import com.example.opencell.data.local.preferences.DeveloperPreferencesRepository
import com.example.opencell.data.repository.CallRepositoryImpl
import com.example.opencell.data.repository.FakeCallRecordDao
import com.example.opencell.data.repository.FakeMessageRecordDao
import com.example.opencell.data.repository.MessageRepositoryImpl
import com.example.opencell.domain.model.ApiKeyRecord
import com.example.opencell.domain.model.WebhookRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeDeveloperPreferencesRepository : DeveloperPreferencesRepository {
    private val _developerMode = MutableStateFlow(false)
    private val _apiKey = MutableStateFlow("")
    private val _apiEndpoint = MutableStateFlow("https://opencellid.org/api/v1")
    private val _mockTelephony = MutableStateFlow(true)
    private val _gatewayEnabled = MutableStateFlow(false)
    private val _gatewayPort = MutableStateFlow(8080)
    private val _apiKeys = MutableStateFlow<List<ApiKeyRecord>>(
        listOf(
            ApiKeyRecord(
                id = "default",
                name = "Default Master Key",
                key = "oc_live_opencell_default_key_999",
                scopes = ApiKeyRecord.ALL_SCOPES
            )
        )
    )
    private val _webhooks = MutableStateFlow<List<WebhookRecord>>(emptyList())

    override val developerModeEnabled: Flow<Boolean> = _developerMode
    override val apiKey: Flow<String> = _apiKey
    override val apiEndpoint: Flow<String> = _apiEndpoint
    override val mockTelephonyEnabled: Flow<Boolean> = _mockTelephony
    override val gatewayServerEnabled: Flow<Boolean> = _gatewayEnabled
    override val gatewayPort: Flow<Int> = _gatewayPort
    override val apiKeys: Flow<List<ApiKeyRecord>> = _apiKeys
    override val webhooks: Flow<List<WebhookRecord>> = _webhooks

    override suspend fun setDeveloperModeEnabled(enabled: Boolean) { _developerMode.value = enabled }
    override suspend fun setApiKey(key: String) { _apiKey.value = key }
    override suspend fun setApiEndpoint(endpoint: String) { _apiEndpoint.value = endpoint }
    override suspend fun setMockTelephonyEnabled(enabled: Boolean) { _mockTelephony.value = enabled }
    override suspend fun setGatewayServerEnabled(enabled: Boolean) { _gatewayEnabled.value = enabled }
    override suspend fun setGatewayPort(port: Int) { _gatewayPort.value = port }

    override suspend fun addApiKey(keyRecord: ApiKeyRecord) {
        _apiKeys.value = _apiKeys.value.filterNot { it.id == keyRecord.id } + keyRecord
    }

    override suspend fun revokeApiKey(keyId: String) {
        _apiKeys.value = _apiKeys.value.filterNot { it.id == keyId }
    }

    override suspend fun addWebhook(webhookRecord: WebhookRecord) {
        _webhooks.value = _webhooks.value.filterNot { it.id == webhookRecord.id } + webhookRecord
    }

    override suspend fun deleteWebhook(webhookId: String) {
        _webhooks.value = _webhooks.value.filterNot { it.id == webhookId }
    }

    override suspend fun clearAll() {
        _developerMode.value = false
        _apiKey.value = ""
        _apiEndpoint.value = "https://opencellid.org/api/v1"
        _mockTelephony.value = true
        _gatewayEnabled.value = false
        _gatewayPort.value = 8080
        _apiKeys.value = emptyList()
        _webhooks.value = emptyList()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DeveloperViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var callRepository: CallRepositoryImpl
    private lateinit var messageRepository: MessageRepositoryImpl
    private lateinit var prefsRepository: FakeDeveloperPreferencesRepository
    private lateinit var viewModel: DeveloperViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        callRepository = CallRepositoryImpl(FakeCallRecordDao())
        messageRepository = MessageRepositoryImpl(FakeMessageRecordDao())
        prefsRepository = FakeDeveloperPreferencesRepository()

        viewModel = DeveloperViewModel(
            developerPreferencesRepository = prefsRepository,
            callRepository = callRepository,
            messageRepository = messageRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setDeveloperMode_updatesPreferenceAndStatus() = runTest(testDispatcher) {
        viewModel.setDeveloperMode(true)
        testScheduler.advanceUntilIdle()

        assertTrue(prefsRepository.developerModeEnabled.first())
        assertNotNull(viewModel.statusMessage.value)
    }

    @Test
    fun generateApiKey_addsNewApiKeyRecord() = runTest(testDispatcher) {
        viewModel.generateApiKey("oc_live_", "Test Application Key", listOf("calls:read", "messages:read"))
        testScheduler.advanceUntilIdle()

        val keys = prefsRepository.apiKeys.first()
        assertTrue(keys.any { it.name == "Test Application Key" && it.key.startsWith("oc_live_") })
    }

    @Test
    fun addAndRemoveWebhook_updatesRepository() = runTest(testDispatcher) {
        viewModel.addWebhook("https://example.com/webhook", listOf("call.created"), "secret123")
        testScheduler.advanceUntilIdle()

        val webhooks = prefsRepository.webhooks.first()
        assertEquals(1, webhooks.size)
        assertEquals("https://example.com/webhook", webhooks[0].url)

        val webhookId = webhooks[0].id
        viewModel.deleteWebhook(webhookId)
        testScheduler.advanceUntilIdle()

        val updatedWebhooks = prefsRepository.webhooks.first()
        assertEquals(0, updatedWebhooks.size)
    }

    @Test
    fun populateSampleData_insertsCallsAndMessages() = runTest(testDispatcher) {
        viewModel.populateSampleData()
        testScheduler.advanceUntilIdle()

        val calls = callRepository.getAllCalls().first()
        val messages = messageRepository.getAllMessages().first()

        assertEquals(2, calls.size)
        assertEquals(1, messages.size)
        assertNotNull(viewModel.statusMessage.value)
    }

    @Test
    fun clearDatabase_removesAllRecords() = runTest(testDispatcher) {
        viewModel.populateSampleData()
        testScheduler.advanceUntilIdle()

        viewModel.clearDatabase()
        testScheduler.advanceUntilIdle()

        val calls = callRepository.getAllCalls().first()
        val messages = messageRepository.getAllMessages().first()

        assertEquals(0, calls.size)
        assertEquals(0, messages.size)
    }
}
