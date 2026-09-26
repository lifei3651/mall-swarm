-- Synthetic only. Run inside a rollback-only test or an empty isolated schema.
-- 994401: full refund; 02: partial refund; 03: processing; 04-06: unpaid/invalid;
-- 07: completed; 08: full refund with an unreversed bonus; 09: another tenant.
UPDATE dms_finance_risk_rule SET enabled=0;
INSERT INTO dms_shop_order
 (id,order_no,tenant_id,user_id,receiver_name,receiver_phone,receiver_address,total_amount,discount_amount,pay_amount,status,pay_time)
VALUES
 (994401,'FIN-CURRENT-01',9944,9944,'fixture','13900000000','fixture',100,0,100,4,'2026-09-01 12:00:00'),
 (994402,'FIN-CURRENT-02',9944,9944,'fixture','13900000000','fixture',100,0,120,2,'2026-09-01 13:00:00'),
 (994403,'FIN-CURRENT-03',9944,9944,'fixture','13900000000','fixture',100,0,100,1,'2026-09-02 12:00:00'),
 (994404,'FIN-CURRENT-04',9944,9944,'fixture','13900000000','fixture',100,0,100,0,NULL),
 (994405,'FIN-CURRENT-05',9944,9944,'fixture','13900000000','fixture',100,0,100,4,NULL),
 (994406,'FIN-CURRENT-06',9944,9944,'fixture','13900000000','fixture',100,0,100,0,'2026-09-02 13:00:00'),
 (994407,'FIN-CURRENT-07',9944,9944,'fixture','13900000000','fixture',100,0,100,3,'2026-09-02 14:00:00'),
 (994408,'FIN-CURRENT-08',9944,9944,'fixture','13900000000','fixture',100,0,100,4,'2026-09-02 15:00:00'),
 (994409,'FIN-CURRENT-09',9945,9945,'fixture','13900000000','fixture',100,0,100,3,'2026-09-02 15:00:00');
INSERT INTO dms_order_finance
 (order_id,order_no,pay_amount,product_cost,refund_amount,net_pay_amount,bonus_amount,company_share_amount,company_profit,risk_status)
SELECT id,order_no,pay_amount,60,999,999,999,999,999,1 FROM dms_shop_order
WHERE id BETWEEN 994401 AND 994409;
INSERT INTO dms_finance_refund (id,order_id,refund_no,refund_amount,product_refund_amount)
VALUES (994401,994401,'FIN-REFUND-01',100,100),
       (994402,994402,'FIN-REFUND-02A',20,20),
       (994403,994402,'FIN-REFUND-02B',20,20),
       (994408,994408,'FIN-REFUND-08',100,100);
INSERT INTO dms_shop_order_item (id,order_id,order_no,product_id,product_name,quantity,cost_amount,total_cost)
VALUES (994402,994402,'FIN-CURRENT-02',1,'fixture',5,12,60),
       (994403,994403,'FIN-CURRENT-03',1,'fixture',5,12,60);
INSERT INTO dms_shop_after_sale (id,after_sale_no,order_id,order_no,member_id,user_id,apply_type,status)
VALUES (994402,'FIN-AS-02',994402,'FIN-CURRENT-02',9944,9944,2,1),
       (994403,'FIN-AS-03',994403,'FIN-CURRENT-03',9944,9944,2,6);
INSERT INTO dms_shop_after_sale_item (after_sale_id,order_id,order_item_id,product_id,refund_quantity)
VALUES (994402,994402,994402,1,2), (994403,994403,994403,1,2);
INSERT INTO dms_commission_record
 (id,tenant_id,record_no,order_id,order_no,order_amount,order_user_id,agent_id,agent_user_id,
  agent_level,commission_level,bonus_type,commission_rate,commission_amount,status)
VALUES
 (994401,9944,'FIN-COM-01',994402,'FIN-CURRENT-02',100,9944,994401,994401,1,1,'DIRECT_REWARD',0.1,10,0),
 (994402,9944,'FIN-COM-02',994402,'FIN-CURRENT-02',100,9944,994402,994402,1,1,'DIRECT_REWARD',0.2,20,1),
 (994403,9944,'FIN-COM-03',994402,'FIN-CURRENT-02',100,9944,994403,994403,1,1,'DIRECT_REWARD',0.3,30,2),
 (994404,9944,'FIN-COM-04',994402,'FIN-CURRENT-02',100,9944,994404,994404,1,1,'DIRECT_REWARD',0.3,30,3),
 (994405,9945,'FIN-COM-OTHER',994402,'FIN-CURRENT-02',100,9944,994405,994405,1,1,'DIRECT_REWARD',0.9,90,1),
 (994408,9944,'FIN-COM-08',994408,'FIN-CURRENT-08',100,9944,994408,994408,1,1,'DIRECT_REWARD',0.1,10,1);
INSERT INTO dms_commission_clawback
 (tenant_id,refund_id,commission_record_id,order_id,agent_id,clawback_amount)
VALUES (9944,994402,994401,994402,994401,5),
       (9944,994402,994402,994402,994402,2),
       (9944,994403,994402,994402,994402,3),
       (9945,994402,994402,994402,994402,99);
INSERT INTO dms_order_company_share (order_id,share_amount)
VALUES (994402,1), (994402,3);
