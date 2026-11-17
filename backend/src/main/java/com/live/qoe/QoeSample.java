package com.live.qoe;

import com.live.broadcast.Broadcast;
import jakarta.persistence.*;

import java.time.Instant;

/** 시청자 한 명이 일정 구간(약 10초) 동안 측정한 수신 품질 */
@Entity
@Table(name = "qoe_samples", indexes = @Index(name = "idx_qoe_broadcast_time", columnList = "broadcast_id, created_at"))
public class QoeSample {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "broadcast_id")
    private Broadcast broadcast;

    @Column(nullable = false, length = 64)
    private String viewerKey;

    @Column(nullable = false) private int bitrateKbps;
    @Column(nullable = false) private double packetLossPct;
    @Column(nullable = false) private double jitterMs;
    @Column(nullable = false) private double rttMs;
    @Column(nullable = false) private double fps;
    @Column(nullable = false) private int framesDropped;
    @Column(nullable = false) private int freezeCount;
    @Column(nullable = false) private int frameHeight;

    @Column(nullable = false, length = 10)
    private String quality;

    @Column(nullable = false)
    private Instant createdAt;

    protected QoeSample() {}

    public QoeSample(Broadcast broadcast, String viewerKey, QoeDtos.SampleRequest r, Instant now) {
        this.broadcast = broadcast;
        this.viewerKey = viewerKey;
        this.bitrateKbps = r.bitrateKbps();
        this.packetLossPct = r.packetLossPct();
        this.jitterMs = r.jitterMs();
        this.rttMs = r.rttMs();
        this.fps = r.fps();
        this.framesDropped = r.framesDropped();
        this.freezeCount = r.freezeCount();
        this.frameHeight = r.frameHeight();
        this.quality = r.quality();
        this.createdAt = now;
    }

    public String getViewerKey() { return viewerKey; }
    public int getBitrateKbps() { return bitrateKbps; }
    public double getPacketLossPct() { return packetLossPct; }
    public double getJitterMs() { return jitterMs; }
    public double getRttMs() { return rttMs; }
    public double getFps() { return fps; }
    public int getFramesDropped() { return framesDropped; }
    public int getFreezeCount() { return freezeCount; }
    public int getFrameHeight() { return frameHeight; }
    public String getQuality() { return quality; }
    public Instant getCreatedAt() { return createdAt; }
}
