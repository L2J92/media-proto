-- V8: 방송자가 녹화를 중지 요청했는지 (방송 중에도 완료 확정 대상)
alter table recordings add column stop_requested boolean default false not null;
