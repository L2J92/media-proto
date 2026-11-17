-- V7: 녹화 실패 사유 (LiveKit Egress error)
alter table recordings add column fail_reason varchar(300);
