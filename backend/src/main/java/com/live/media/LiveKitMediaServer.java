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
    public Optional<String> startRecording(String roomName, String filePath) {
        RecordingProperties.S3 s3 = recordingProps.s3();
        if (!recordingProps.enabled() || s3 == null || s3.bucket() == null || s3.bucket().isBlank()) {
            return Optional.empty();
        }
        LivekitEgress.S3Upload.Builder upload = LivekitEgress.S3Upload.newBuilder()
                .setAccessKey(s3.accessKey())
                .setSecret(s3.secret())
                .setBucket(s3.bucket())
                .setRegion(s3.region() == null ? "auto" : s3.region());
        if (s3.endpoint() != null && !s3.endpoint().isBlank()) {
            upload.setEndpoint(s3.endpoint()).setForcePathStyle(true);
        }
        LivekitEgress.EncodedFileOutput output = LivekitEgress.EncodedFileOutput.newBuilder()
                .setFileType(LivekitEgress.EncodedFileType.MP4)
                .setFilepath(filePath)
                .setS3(upload.build())
                .build();
        LivekitEgress.EgressInfo info = execute(egressClient.startRoomCompositeEgress(roomName, output, "speaker"));
        return Optional.of(info.getEgressId());
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
                boolean ok = info.getStatus() == LivekitEgress.EgressStatus.EGRESS_COMPLETE;
                String location = info.getFileResultsCount() > 0 ? info.getFileResults(0).getLocation() : null;
                long durationMs = info.getFileResultsCount() > 0 ? info.getFileResults(0).getDuration() / 1_000_000 : 0;
                yield new MediaEvent(MediaEvent.Type.EGRESS_ENDED, info.getRoomName(), null, info.getEgressId(), ok, location, durationMs);
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
