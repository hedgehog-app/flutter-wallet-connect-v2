package com.avacus.wallet_connect_v2

import android.app.Activity
import android.app.Application
import androidx.annotation.NonNull
import com.google.gson.Gson
import com.walletconnect.android.Core
import com.walletconnect.android.CoreClient
import com.walletconnect.android.relay.ConnectionType
import com.walletconnect.android.relay.RelayClient
import com.walletconnect.sign.client.Sign
import com.walletconnect.sign.client.SignClient
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

/** WalletConnectV2Plugin */
class WalletConnectV2Plugin : FlutterPlugin, MethodCallHandler, ActivityAware,
    EventChannel.StreamHandler {
    private lateinit var context: Application
    private var activity: Activity? = null

    private lateinit var channel: MethodChannel
    private lateinit var eventChannel: EventChannel
    private var eventSink: EventChannel.EventSink? = null
    private var pendingPairings: MutableList<Core.Model.Pairing> = mutableListOf()

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        context = flutterPluginBinding.applicationContext as Application
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "wallet_connect_v2")
        channel.setMethodCallHandler(this)
        eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, "wallet_connect_v2/event")
        eventChannel.setStreamHandler(this)
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            "init" -> {
                val arguments = call.arguments<Map<String, Any>>()
                val projectId = arguments?.get("projectId")
                val serverUrl = "wss://relay.walletconnect.com?projectId=$projectId"
                val connectionType = ConnectionType.MANUAL

                val gson = Gson()
                val appMetaData = gson.fromJson(
                    gson.toJson(arguments?.get("appMetadata")), Core.Model.AppMetaData::class.java
                )

                CoreClient.initialize(
                    relayServerUrl = serverUrl,
                    connectionType = connectionType,
                    application = context,
                    metaData = appMetaData
                ) {
                    onError(
                        "init_core_error", errorMessage = it.throwable.message ?: ""
                    )
                }

                RelayClient.connect(onError = {
                    onError(
                        "connection error",
                        errorMessage = it
                    )
                })

                val init = Sign.Params.Init(core = CoreClient)

                SignClient.initialize(init) {
                    onError("init_sign_error", errorMessage = it.throwable.message ?: "")
                }

                val dappDelegate = object : SignClient.DappDelegate {
                    
                    override fun onSessionApproved(approvedSession: Sign.Model.ApprovedSession) {
                        onEvent(name = "log", data = mapOf("message" to "SESSION SETTLED"))
                        onEvent(
                            name = "session_settle", data = approvedSession.toFlutterValue()
                        )
                    }

                    override fun onSessionRejected(rejectedSession: Sign.Model.RejectedSession) {
                        onEvent(name = "log", data = mapOf("message" to "SESSION REJECTED"))
                        // TBD
                    }

                    override fun onSessionUpdate(updatedSession: Sign.Model.UpdatedSession) {
                        onEvent(name = "log", data = mapOf("message" to "SESSION UPDATED"))
                        // TBD
                    }

                    override fun onSessionEvent(sessionEvent: Sign.Model.SessionEvent) {
                        onEvent(name = "log", data = mapOf("message" to "SESSION EVENT"))
                        // TBD
                    }

                    override fun onSessionDelete(deletedSession: Sign.Model.DeletedSession) {
                        onEvent(name = "log", data = mapOf("message" to "SESSION DELETED"))
                        if (deletedSession is Sign.Model.DeletedSession.Success) {
                            val deletedSessionSuccess = deletedSession as Sign.Model.DeletedSession.Success
                            onEvent(
                                name = "session_delete", data = mapOf(
                                    "topic" to deletedSessionSuccess.topic
                                )
                            )
                        }
                    }

                    override fun onSessionExtend(session: Sign.Model.Session) {
                        onEvent(name = "log", data = mapOf("message" to "SESSION EXTENDED"))
                        // TBD
                    }

                    override fun onSessionRequestResponse(response: Sign.Model.SessionRequestResponse) {
                        onEvent(
                            name = "session_response", data = response.toFlutterValue()
                        )
                    }

                    override fun onConnectionStateChange(state: Sign.Model.ConnectionState) {
                        onEvent(name = "log", data = mapOf("message" to "Connection state change: ${state.isAvailable}"))
                        onEvent(
                            name = "connection_status", data = mapOf(
                                "isConnected" to state.isAvailable
                            )
                        )
                    }

                    override fun onError(error: Sign.Model.Error) {
                        onError(
                            "delegate_error",
                            errorMessage = error.throwable.message ?: ""
                        )
                    }
                }

                SignClient.setDappDelegate(dappDelegate)

                result.success(null)
            }
            "connect" -> {
                CoreClient.Relay.connect {
                    onError("connect_error", errorMessage = it)
                }
                result.success(null)
            }
            "disconnect" -> {
                CoreClient.Relay.disconnect {
                    onError("disconnect_error", errorMessage = it)
                }
                result.success(null)
            }
            "initPairing" -> {
                val pairing = CoreClient.Pairing.create(onError = {
                    onError(
                        "init_pairing_error",
                        errorMessage = it.throwable.message ?: ""
                    )
                })

                if (pairing != null) {
                    this.pendingPairings.add(pairing)
                    result.success(mapOf(
                        "fullUri" to pairing!!.uri,
                        "topic" to pairing!!.topic,
                    ))
                } else {
                    onError(
                        "init_pairing_error",
                        errorMessage = "Got null pairing object"
                    )
                }
            }
            "pair" -> {
                val uri = call.argument<String>("uri")!!
                CoreClient.Pairing.pair(pair = Core.Params.Pair(uri = uri), onError = {
                    onError(
                        "pair_error",
                        errorMessage = it.throwable.message ?: ""
                    )
                })
                result.success(null)
            }
            "approve" -> {
                val arguments = call.arguments<Map<String, Any>>()!!
                val gson = Gson()
                val rawNamespaces = arguments["namespaces"] as Map<*, *>
                val namespaces = rawNamespaces.mapValues {
                    gson.fromJson(
                        gson.toJson(it.value), Sign.Model.Namespace.Session::class.java
                    )
                }
                @Suppress("UNCHECKED_CAST") val approve = Sign.Params.Approve(
                    proposerPublicKey = arguments["id"] as String,
                    namespaces = namespaces as Map<String, Sign.Model.Namespace.Session>
                )
                SignClient.approveSession(approve = approve, onError = {
                    onError(
                        "approve_error",
                        errorMessage = it.throwable.message ?: ""
                    )
                })
                result.success(null)
            }
            "reject" -> {
                val arguments = call.arguments<Map<String, Any>>()!!
                val reject = Sign.Params.Reject(
                    proposerPublicKey = arguments["id"] as String, reason = "user_rejected"
                )
                SignClient.rejectSession(reject = reject, onError = {
                    onError(
                        "reject_error",
                        errorMessage = it.throwable.message ?: ""
                    )
                })
                result.success(null)
            }
            "getActivatedSessions" -> {
                val sessions = SignClient.getListOfSettledSessions()
                result.success(sessions.map { it.toFlutterValue() })
            }
            "connectSession" -> {
                val arguments = call.arguments<Map<String, Any>>()!!
                val gson = Gson()
                val topic = arguments["topic"] as String
                val rawNamespaces = arguments["requiredNamespaces"] as Map<*, *>
                val namespaces = rawNamespaces.mapValues {
                    gson.fromJson(
                        gson.toJson(it.value), Sign.Model.Namespace.Proposal::class.java
                    )
                }

                // find pairing by the input topic ID
                var pairing: Core.Model.Pairing? = null
                for (p in this.pendingPairings) {
                    if (p.topic == topic) {
                        pairing = p
                        break
                    }
                }

                if (pairing == null) {
                    onEvent(name = "log", data = mapOf("message" to "Pairing NOT FOUND"))
                    onError(
                        "connect_session_error", errorMessage = "Pairing topic not recognized"
                    )
                    return
                }

                val connectParams = Sign.Params.Connect(
                    namespaces = namespaces as Map<String, Sign.Model.Namespace.Proposal>,
                    pairing = pairing as Core.Model.Pairing
                )
                SignClient.connect(connect = connectParams, onError = {
                    onError(
                        "connect_session_error", errorMessage = it.throwable.message ?: ""
                    )
                }, onSuccess = {})

                result.success(null)
            }
            "disconnectSession" -> {
                val arguments = call.arguments<Map<String, Any>>()!!
                val disconnectParams = Sign.Params.Disconnect(sessionTopic = arguments["topic"] as String)
                SignClient.disconnect(disconnect = disconnectParams, onError = {
                    onError(
                        "disconnect_session_error", errorMessage = it.throwable.message ?: ""
                    )
                })
                result.success(null)
            }
            "updateSession" -> {
                val arguments = call.arguments<Map<String, Any>>()!!
                val gson = Gson()
                val rawNamespaces = arguments["namespaces"] as Map<*, *>
                val namespaces = rawNamespaces.mapValues {
                    gson.fromJson(
                        gson.toJson(it.value), Sign.Model.Namespace.Session::class.java
                    )
                }
                @Suppress("UNCHECKED_CAST") val update = Sign.Params.Update(
                    sessionTopic = arguments["id"] as String,
                    namespaces = namespaces as Map<String, Sign.Model.Namespace.Session>
                )
                SignClient.update(update = update, onError = {
                    onError(
                        "update_session_error", errorMessage = it.throwable.message ?: ""
                    )
                })
                result.success(null)
            }
            "sendRequest" -> {
                val arguments = call.arguments<Map<String, Any>>()!!
                val gson = Gson()
                val topic = arguments["topic"] as String
                val method = arguments["method"] as String
                val chainId = arguments["chainId"] as String
                val params = arguments["params"] as List<Map<String, Any>>
                val request = Sign.Params.Request(
                    sessionTopic = arguments["topic"] as String,
                    method = method,
                    chainId = chainId,
                    params = gson.toJson(params),
                )

                SignClient.request(request = request, onError = {
                    onError("reject_request_error", errorMessage = it.throwable.message ?: "")
                })
                result.success(null)
            }
            "approveRequest" -> {
                val arguments = call.arguments<Map<String, Any>>()!!
                SignClient.respond(response = Sign.Params.Response(
                    sessionTopic = arguments["topic"] as String,
                    Sign.Model.JsonRpcResponse.JsonRpcResult(
                        id = (arguments["requestId"] as String).toLong(),
                        result = arguments["result"] as String
                    )
                ), onError = {
                    onError(
                        "approve_request_error",
                        errorMessage = it.throwable.message ?: ""
                    )
                })
                result.success(null)
            }
            "rejectRequest" -> {
                val arguments = call.arguments<Map<String, Any>>()!!
                SignClient.respond(response = Sign.Params.Response(
                    sessionTopic = arguments["topic"] as String,
                    Sign.Model.JsonRpcResponse.JsonRpcError(
                        id = (arguments["requestId"] as String).toLong(),
                        code = 4001,
                        message = "User rejected the request"
                    )
                ), onError = {
                    onError("reject_request_error", errorMessage = it.throwable.message ?: "")
                })
                result.success(null)
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        eventSink = events
    }

    override fun onCancel(arguments: Any?) {
        eventSink = null
    }

    private fun onEvent(name: String, data: Any) {
        activity?.runOnUiThread {
            eventSink?.success(
                mapOf(
                    "name" to name, "data" to data
                )
            )
        }
    }

    private fun onError(code: String, errorMessage: String = "") {
        activity?.runOnUiThread {
            eventSink?.error(code, errorMessage, null)
        }
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    }

    override fun onDetachedFromActivity() {
        activity = null
    }
}

fun Sign.Model.SessionProposal.toFlutterValue(): Map<String, Any> {
    return mapOf(
        "id" to this.proposerPublicKey,
        "proposer" to mapOf(
            "name" to this.name,
            "description" to this.description,
            "url" to this.url,
            "icons" to this.icons.map { it.toString() },
        ),
        "namespaces" to this.requiredNamespaces.map { (key, value) ->
            key to mapOf(
                "chains" to value.chains,
                "methods" to value.methods,
                "events" to value.events,
                "extensions" to value.extensions?.map {
                    mapOf(
                        "chains" to it.chains,
                        "methods" to it.methods,
                        "events" to it.events,
                    )
                },
            )
        }.toMap()
    )
}

fun Sign.Model.Session.toFlutterValue(): Map<String, Any> {
    return mapOf(
        "topic" to this.topic,
        "peer" to mapOf(
            "name" to this.metaData?.name,
            "description" to this.metaData?.description,
            "url" to this.metaData?.url,
            "icons" to this.metaData?.icons,
        ),
        "expiration" to Date(this.expiry).toUtcIsoDateString(),
        "namespaces" to this.namespaces.map { (key, value) ->
            key to mapOf(
                "accounts" to value.accounts,
                "methods" to value.methods,
                "events" to value.events,
                "extensions" to value.extensions?.map {
                    mapOf(
                        "accounts" to it.accounts,
                        "methods" to it.methods,
                        "events" to it.events,
                    )
                },
            )
        }.toMap()
    )
}

fun Sign.Model.ApprovedSession.toFlutterValue(): Map<String, Any> {
    val gson = Gson()
    return mapOf(
        "topic" to this.topic,
        "peer" to mapOf(
            "name" to this.metaData?.name,
            "description" to this.metaData?.description,
            "url" to this.metaData?.url,
            "icons" to this.metaData?.icons,
        ),
        "expiration" to "1970-01-01T00:00:00.000Z", // not modeled in ApprovedSession
        "namespaces" to this.namespaces.map { (key, value) ->
            key to mapOf(
                "accounts" to value.accounts,
                "methods" to value.methods,
                "events" to value.events,
                "extensions" to value.extensions?.map {
                    mapOf(
                        "accounts" to it.accounts,
                        "methods" to it.methods,
                        "events" to it.events,
                    )
                },
            )
        }.toMap()
    )
}

fun Sign.Model.SessionRequest.toFlutterValue(): Map<String, String?> {
    return mapOf(
        "id" to this.request.id.toString(),
        "topic" to this.topic,
        "chainId" to this.chainId,
        "method" to this.request.method,
        "params" to this.request.params
    )
}

fun Sign.Model.SessionRequestResponse.toFlutterValue(): Map<String, String?> {
    val gson = Gson()
    return mapOf(
        "id" to this.result.id.toString(),
        "topic" to this.topic,
        "chainId" to this.chainId,
        "method" to this.method,
        "result" to gson.toJson(this.result)
    )
}

fun Date.toUtcIsoDateString(): String {
    val dateFormat: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    return dateFormat.format(this)
}