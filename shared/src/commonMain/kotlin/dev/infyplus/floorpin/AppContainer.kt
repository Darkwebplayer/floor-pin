package dev.infyplus.floorpin

import dev.infyplus.floorpin.data.auth.GoogleAuthProvider
import dev.infyplus.floorpin.data.auth.SessionManager
import dev.infyplus.floorpin.data.auth.TokenStore
import dev.infyplus.floorpin.data.db.DriverFactory
import dev.infyplus.floorpin.data.db.createDatabase
import dev.infyplus.floorpin.data.remote.ApiService
import dev.infyplus.floorpin.data.remote.createHttpClient
import dev.infyplus.floorpin.data.repo.DataStore
import dev.infyplus.floorpin.data.sync.SyncEngine
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual DI graph, built once per process by the platform entry point. */
class AppContainer(
    val tokens: TokenStore,
    val google: GoogleAuthProvider,
    val http: HttpClient,
    val api: ApiService,
    val data: DataStore,
    val session: SessionManager,
    val sync: SyncEngine,
)

fun buildAppContainer(
    tokens: TokenStore,
    driverFactory: DriverFactory,
    google: GoogleAuthProvider,
): AppContainer {
    val http = createHttpClient(tokens)
    val api = ApiService(http)
    val data = DataStore(createDatabase(driverFactory))
    val session = SessionManager(tokens, google, api, data)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val sync = SyncEngine(data, api, scope)
    return AppContainer(tokens, google, http, api, data, session, sync)
}
