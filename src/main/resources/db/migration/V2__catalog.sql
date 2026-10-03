INSERT INTO games (id,seed_key,title,genre,price,image) VALUES
(1,'cs2','CS2','Шутер',0,'CS2.jpg'),
(2,'minecraft','Minecraft','Песочница',1000,'minecraft.jpg'),
(3,'gta5','GTA V','Экшен',2000,'GTA5.jpg'),
(4,'cyberpunk','Cyberpunk 2077','RPG',15000,'Cyberpunk 2077.jpg'),
(5,'eldenring','Elden Ring','RPG',18000,'Elden Ring.jpg'),
(6,'dota2','Dota 2','MOBA',0,'Dota 2.jpg'),
(7,'valorant','Valorant','Шутер',0,'Valorant.jpg'),
(8,'rdr2','Red Dead Redemption 2','Экшен',12000,'rdr2.jpg'),
(9,'witcher3','The Witcher 3','RPG',8000,'witcher3.jpg'),
(10,'fifa24','FIFA 24','Спорт',14000,'FIFA.jpg'),
(11,'forza5','Forza Horizon 5','Гонки',16000,'Forza Horizon 5.jpg'),
(12,'terraria','Terraria','Песочница',2500,'terraria icon.jpg'),
(13,'stardew','Stardew Valley','Симулятор',3000,'Stardew Valley.jpg'),
(14,'hades','Hades','Экшен',4500,'HADES.jpg'),
(15,'hollowknight','Hollow Knight','Платформер',3500,'hollow knight.jpg'),
(16,'sekiro','Sekiro','Экшен',13000,'sekiro.jpg'),
(17,'darksouls3','Dark Souls III','RPG',11000,'dark souls3.jpg'),
(18,'residentevil4','Resident Evil 4','Хоррор',17000,'resident 4.jpg'),
(19,'godofwar','God of War','Экшен',19000,'godofwar.jpg'),
(20,'portal2','Portal 2','Головоломка',2000,'Portal2.jpg')
ON CONFLICT (seed_key) DO NOTHING;
SELECT setval(pg_get_serial_sequence('games','id'), (SELECT max(id) FROM games));
