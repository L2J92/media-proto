package com.live.recording;

import com.live.broadcast.Broadcast;
import com.live.common.BaseTimeEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "recordings", indexes = @Index(name = "idx_recording_egress", columnList = "egressId", unique = true))
public class Recording extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "broadcast_id")
    private Broadcast broadcast;

    @Column(nullable = false, length = 64)
    private String egressId;

    /** 저장소 안의 파일 경로(key) */
    @Column(nullable = false, length = 300)
    private String filePath;

    /** 미디어 서버가 보고한 실제 저장 위치 */
    @Column(length = 500)
    private String location;

    private Long durationMillis;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RecordingStatus status = RecordingStatus.RECORDING;

    protected Recording() {}

    public Recording(Broadcast broadcast, String egressId, String filePath) {
        this.broadcast = broadcast;
        this.egressId = egressId;
        this.filePath = filePath;
    }

    /** 같은 종료 통지가 여러 번 와도 상태는 한 번만 바뀐다. */
    public void complete(String location, long durationMillis) {
        if (status != RecordingStatus.RECORDING) return;
        this.status = RecordingStatus.COMPLETED;
        this.location = location;
        this.durationMillis = durationMillis;
    }

    public void fail() {
        if (status != RecordingStatus.RECORDING) return;
        this.status = RecordingStatus.FAILED;
    }

    public Long getId() { return id; }
    public Broadcast getBroadcast() { return broadcast; }
    public String getEgressId() { return egressId; }
    public String getFilePath() { return filePath; }
    public String getLocation() { return location; }
    public Long getDurationMillis() { return durationMillis; }
    public RecordingStatus getStatus() { return status; }
}
