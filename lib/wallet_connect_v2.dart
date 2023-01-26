import 'dart:async';

import 'package:flutter/services.dart';

import 'src/model/app_metadata.dart';
import 'src/model/connection_status.dart';
import 'src/model/log_data.dart';
import 'src/model/proposal_namespace.dart';
import 'src/model/session.dart';
import 'src/model/session_approval.dart';
import 'src/model/session_delete.dart';
import 'src/model/session_proposal.dart';
import 'src/model/session_request.dart';
import 'src/model/session_response.dart';
import 'src/model/session_update.dart';
import 'src/model/walletconnect_uri.dart';
import 'wallet_connect_v2_platform_interface.dart';

export 'src/model/app_metadata.dart';
export 'src/model/log_data.dart';
export 'src/model/proposal_namespace.dart';
export 'src/model/session.dart';
export 'src/model/session_approval.dart';
export 'src/model/session_namespace.dart';
export 'src/model/session_proposal.dart';
export 'src/model/session_request.dart';
export 'src/model/session_response.dart';
export 'src/model/walletconnect_uri.dart';

class WalletConnectV2 {
  StreamSubscription? _eventSubscription;

  OnConnectionStatus? onConnectionStatus;
  OnSessionProposal? onSessionProposal;
  OnSessionSettle? onSessionSettle;
  OnSessionUpdate? onSessionUpdate;
  OnSessionDelete? onSessionDelete;
  OnSessionRequest? onSessionRequest;
  OnSessionResponse? onSessionResponse;
  OnEventError? onEventError;
  OnLogData? onLogData;

  Future<void> init(
      {required String projectId, required AppMetadata appMetadata}) {
    _eventSubscription =
        WalletConnectV2Platform.instance.onEvent.listen((event) {
      if (event is ConnectionStatus) {
        onConnectionStatus?.call(event.isConnected);
      } else if (event is SessionProposal) {
        onSessionProposal?.call(event);
      } else if (event is Session) {
        onSessionSettle?.call(event);
      } else if (event is SessionRequest) {
        onSessionRequest?.call(event);
      } else if (event is SessionResponse) {
        onSessionResponse?.call(event);
      } else if (event is SessionUpdate) {
        onSessionUpdate?.call(event.topic);
      } else if (event is SessionDelete) {
        onSessionDelete?.call(event.topic);
      } else if (event is LogData) {
        onLogData?.call(event.message);
      }
    }, onError: (error) {
      if (error is PlatformException) {
        onEventError?.call(error.code, error.message ?? "Internal error");
      } else {
        onEventError?.call('general_error', error.toString());
      }
    });
    return WalletConnectV2Platform.instance
        .init(projectId: projectId, appMetadata: appMetadata);
  }

  Future<void> connect() {
    return WalletConnectV2Platform.instance.connect();
  }

  Future<void> disconnect() {
    return WalletConnectV2Platform.instance.disconnect();
  }

  Future<void> pair({required String uri}) {
    return WalletConnectV2Platform.instance.pair(uri: uri);
  }

  Future<WalletConnectUri?> initPairing() {
    return WalletConnectV2Platform.instance.initPairing();
  }

  Future<void> approveSession({required SessionApproval approval}) {
    return WalletConnectV2Platform.instance.approve(approval: approval);
  }

  Future<void> rejectSession({required String proposalId}) {
    return WalletConnectV2Platform.instance.reject(proposalId: proposalId);
  }

  Future<List<Session>> getActivatedSessions() {
    return WalletConnectV2Platform.instance.getActivatedSessions();
  }

  Future<void> connectSession(
      {required String topic,
      required Map<String, ProposalNamespace> requiredNamespaces}) {
    return WalletConnectV2Platform.instance
        .connectSession(topic: topic, requiredNamespaces: requiredNamespaces);
  }

  Future<void> disconnectSession({required String topic}) {
    return WalletConnectV2Platform.instance.disconnectSession(topic: topic);
  }

  Future<void> updateSession({required SessionApproval updateApproval}) {
    return WalletConnectV2Platform.instance
        .updateSession(updateApproval: updateApproval);
  }

  Future<void> sendRequest({required SessionRequest request}) {
    return WalletConnectV2Platform.instance.sendRequest(request: request);
  }

  Future<void> approveRequest(
      {required String topic,
      required String requestId,
      required String result}) {
    return WalletConnectV2Platform.instance
        .approveRequest(topic: topic, requestId: requestId, result: result);
  }

  Future<void> rejectRequest(
      {required String topic, required String requestId}) {
    return WalletConnectV2Platform.instance
        .rejectRequest(topic: topic, requestId: requestId);
  }

  Future dispose() async {
    _eventSubscription?.cancel();
    _eventSubscription = null;
  }
}

typedef OnConnectionStatus = Function(bool isConnected);
typedef OnSessionProposal = Function(SessionProposal proposal);
typedef OnSessionSettle = Function(Session session);
typedef OnSessionUpdate = Function(String topic);
typedef OnSessionDelete = Function(String topic);
typedef OnSessionRequest = Function(SessionRequest request);
typedef OnSessionResponse = Function(SessionResponse response);
typedef OnEventError = Function(String code, String message);
typedef OnLogData = Function(String message);
