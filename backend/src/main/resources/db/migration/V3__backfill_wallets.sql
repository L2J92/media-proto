-- V3: 지갑을 회원 가입 시점에 만들도록 바뀌어, 지갑이 없는 기존 회원에게 빈 지갑을 만들어 준다.
insert into wallets (user_id, balance, version, created_at, updated_at)
select u.id, 0, 0, current_timestamp, current_timestamp
from users u
where not exists (select 1 from wallets w where w.user_id = u.id);
