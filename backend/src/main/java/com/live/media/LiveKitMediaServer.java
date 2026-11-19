package com.live.media;

import com.live.common.ApiException;
import com.live.common.ErrorCode;
import com.live.recording.RecordingProperties;
import io.livekit.server.*;
import livekit.LivekitEgress;
import livekit.LivekitModels;
import livekit.LivekitWebhook;
import org.springframework.stereotype.Component;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class LiveKitMediaServer implements MediaServer {
    private static final long TOKEN_TTL_MILLIS = 6 * 60 * 60 * 1000L;
    /** 아무도 없는 방은 5분 뒤 미디어 서버가 정리한다. */
    private static final int EMPTY_ROOM_TIMEOUT_SECONDS = 300;

    private final LiveKitProperties props;
    private final RecordingProperties recordingProps;
    private final RoomServiceClient roomClient;
    private final EgressServiceClient egressClient;
    private final WebhookReceiver webhookReceiver;

    public LiveKitMediaServer(LiveKitProperties props, RecordingProperties recordingProps) {
        this.props = props;
        this.recordingProps = recordingProps;
        if (props.configured()) {
            this.roomClient = RoomServiceClient.createClient(props.httpUrl(), props.apiKey(), props.apiSecret());
            this.egressClient = EgressServiceClient.createClient(props.httpUrl(), props.apiKey(), props.apiSecret());
            this.webhookReceiver = new WebhookReceiver(props.apiKey(), props.apiSecret());
        } else {
            this.roomClient = null;
            this.egressClient = null;
            this.webhookReceiver = null;
        }
    }

    @Override
    public boolean isConfigured() {
        return props.configured();
    }

    @Override
    public String clientUrl() {
        return props.url();
    }

    @Override
    public void createRoom(String roomName) {
        execute(roomClient.createRoom(roomName, EMPTY_ROOM_TIMEOUT_SECONDS, 0));
    }

    @Override
    public void deleteRoom(String roomName) {
        try {
            Response<?> res = roomClient.deleteRoom(roomName).execute();
            // 이미 없는 방(404)은 정리된 것으로 본다.
            if (!res.isSuccessful() && res.code() != 404) {
                throw new ApiException(ErrorCode.MEDIA_SERVER_ERROR);
            }
        } catch (IOException e) {
            throw new ApiException(ErrorCode.MEDIA_SERVER_ERROR, e);
        }
    }

    @Override
    public String issuePublisherToken(String roomName, String identity, String displayName) {
        AccessToken token = baseToken(identity, displayName);
        token.addGrants(new RoomJoin(true), new RoomName(roomName),
                new CanPublish(true), new CanSubscribe(true), new CanPublishData(false),
                new CanPublishSources(List.of("camera", "microphone", "screen_share", "screen_share_audio")));
        return token.toJwt();
    }

    @Override
    public String issueViewerToken(String roomName, String identity, String displayName) {
        AccessToken token = baseToken(identity, displayName);
        // 시청자는 영상·음성·데이터 어떤 것도 송출할 수 없다. 채팅은 서버 API 를 거친다.
        token.addGrants(new RoomJoin(true), new RoomName(roomName),
                new CanPublish(false), new CanSubscribe(true), new CanPublishData(false));
        return token.toJwt();
    }

    @Override
    public Set<String> participantIdentities(String roomName) {
        try {
            Response<List<LivekitModels.ParticipantInfo>> res = roomClient.listParticipants(roomName).execute();
            if (res.code() == 404) return Set.of();
            if (!res.isSuccessful() || res.body() == null) throw new ApiException(ErrorCode.MEDIA_SERVER_ERROR);
            return res.body().stream().map(LivekitModels.ParticipantInfo::getIdentity).collect(Collectors.toSet());
        } catch (IOException e) {
            throw new ApiException(ErrorCode.MEDIA_SERVER_ERROR, e);
        }
    }

    @Override
    public void sendData(String roomName, String topic, byte[] payload) {
        execute(roomClient.sendData(roomName, payload, LivekitModels.DataPacket.Kind.RELIABLE,
                List.of(), List.of(), topic));
    }

    @Override
    public Optional<String> startRecording(String roomName, String playlistPath) {
        RecordingProperties.S3 s3 = recordingProps.s3();
        if (!recordingProps.enabled() || s3 == null || s3.bucket() == null || s3.bucket().isBlank()) {
            return Optional.empty();
        }
        LivekitEgress.S3Upload.Builder upload = LivekitEgress.S3Upload.newBuilder()
                .setAccessKey(s3.accessKey())
                .setSecret(s3.secret())
                .setBucket(s3.bucket())
                .setRegion(s3.region() == null || s3.region().isBlank() ? "auto" : s3.region());
        if (s3.endpoint() != null && !s3.endpoint().isBlank()) {
            upload.setEndpoint(s3.endpoint()).setForcePathStyle(true);
        }
        String dir = playlistPath.substring(0, playlistPath.lastIndexOf('/'));
        // HLS: 6초 세그먼트(.ts) + 플레이리스트. 파일 하나가 작아 무료 저장소의 파일 크기 제한에 걸리지 않고,
        // 재생 시 처음부터 전부 받지 않아도 된다.
        LivekitEgress.SegmentedFileOutput output = LivekitEgress.SegmentedFileOutput.newBuilder()
                .setProtocol(LivekitEgress.SegmentedFileProtocol.HLS_PROTOCOL)
                .setFilenamePrefix(dir + "/segment")
                .setPlaylistName(playlistPath)
                .setSegmentDuration(6)
                .setS3(upload.build())
                .build();
        LivekitEgress.EgressInfo info = execute(egressClient.startRoomCompositeEgress(roomName, output, "speaker"));
        return Optional.of(info.getEgressId());
    }

    @Override
    public void stopRecording(String egressId) {
        try {
            Response<LivekitEgress.EgressInfo> res = egressClient.stopEgress(egressId).execute();
            // 이미 끝난 녹화는 오류로 오지만, 중지 목적은 달성된 것이므로 무시한다
            if (!res.isSuccessful() && res.code() >= 500) throw new ApiException(ErrorCode.MEDIA_SERVER_ERROR);
        } catch (IOException e) {
            throw new ApiException(ErrorCode.MEDIA_SERVER_ERROR, e);
        }
    }

    @Override
    public Optional<RecordingState> findRecording(String egressId) {
        List<LivekitEgress.EgressInfo> list = execute(egressClient.listEgress(null, egressId));
        if (list == null || list.isEmpty()) return Optional.empty();
        return Optional.of(toRecordingState(list.get(0)));
    }

    private static RecordingState toRecordingState(LivekitEgress.EgressInfo info) {
        LivekitEgress.EgressStatus st = info.getStatus();
        boolean finished = st == LivekitEgress.EgressStatus.EGRESS_COMPLETE
                || st == LivekitEgress.EgressStatus.EGRESS_FAILED
                || st == LivekitEgress.EgressStatus.EGRESS_ABORTED
                || st == LivekitEgress.EgressStatus.EGRESS_LIMIT_REACHED;
        // 무료 한도에 걸려 중간에 끊긴 경우도 그때까지의 영상은 재생 가능하므로 성공으로 본다.
        boolean ok = st == LivekitEgress.EgressStatus.EGRESS_COMPLETE || st == LivekitEgress.EgressStatus.EGRESS_LIMIT_REACHED;
        String location = null;
        long durationMs = 0;
        if (info.getSegmentResultsCount() > 0) {
            location = info.getSegmentResults(0).getPlaylistLocation();
            durationMs = info.getSegmentResults(0).getDuration() / 1_000_000;
        } else if (info.getFileResultsCount() > 0) {
            location = info.getFileResults(0).getLocation();
            durationMs = info.getFileResults(0).getDuration() / 1_000_000;
        }
        String error = info.getError() == null || info.getError().isBlank() ? null : info.getError();
        return new RecordingState(finished, ok, location, durationMs, error);
    }

    @Override
    public MediaEvent parseWebhook(String body, String authorization) {
        if (webhookReceiver == null) {
            throw new InvalidWebhookException("media server not configured", null);
        }
        LivekitWebhook.WebhookEvent e;
        try {
            e = webhookReceiver.receive(body, authorization);
        } catch (Exception ex) {
            throw new InvalidWebhookException("invalid signature or payload", ex);
        }
        String room = e.hasRoom() ? e.getRoom().getName() : null;
        String identity = e.hasParticipant() ? e.getParticipant().getIdentity() : null;
        return switch (e.getEvent()) {
            case "participant_joined" -> new MediaEvent(MediaEvent.Type.PARTICIPANT_JOINED, room, identity, null, false, null, 0);
            case "participant_left" -> new MediaEvent(MediaEvent.Type.PARTICIPANT_LEFT, room, identity, null, false, null, 0);
            case "room_finished" -> new MediaEvent(MediaEvent.Type.ROOM_FINISHED, room, null, null, false, null, 0);
            case "egress_ended" -> {
                LivekitEgress.EgressInfo info = e.getEgressInfo();
                RecordingState state = toRecordingState(info);
                yield new MediaEvent(MediaEvent.Type.EGRESS_ENDED, info.getRoomName(), null, info.getEgressId(),
                        state.succeeded(), state.location(), state.durationMillis(), state.error());
            }
            default -> MediaEvent.other();
        };
    }

    private AccessToken baseToken(String identity, String displayName) {
        AccessToken token = new AccessToken(props.apiKey(), props.apiSecret());
        token.setIdentity(identity);
        token.setName(displayName);
        token.setTtl(TOKEN_TTL_MILLIS);
        return token;
    }

    private static <T> T execute(Call<T> call) {
        try {
            Response<T> res = call.execute();
            if (!res.isSuccessful()) {
                throw new ApiException(ErrorCode.MEDIA_SERVER_ERROR);
            }
            return res.body();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.MEDIA_SERVER_ERROR, e);
        }
    }
}
