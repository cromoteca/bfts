--> clientName1
select 'My PC';

--> clientName2
select 'My Laptop';

--> sourceName1
select 'source1';

--> sourceName2
select 'source2';

delete from sources;

insert into sources
  (client        , name          , rootPath             )
values
  (${clientName1}, ${sourceName1}, '/' || ${sourceName1}),
  (${clientName1}, ${sourceName2}, '/' || ${sourceName2}),
  (${clientName2}, ${sourceName1}, '/' || ${sourceName1});

--> sourceId1_1
select id from sources
  where name = ${sourceName1} and client = ${clientName1};

--> sourceId2_1
select id from sources
  where name = ${sourceName2} and client = ${clientName1};

--> sourceId1_2
select id from sources
  where name = ${sourceName1} and client = ${clientName2};

delete from files;

insert into files
  (sourceId      , name   , parent, size, lastModified, created   , status, hash      )
values
  (${sourceId1_1}, 'dir1' , ''    , 0   , null        , 1430316038, 0     , null     ),
  (${sourceId1_1}, 'file1', 'dir1', 100 , 1111        , 1430316038, 0     , x'aabb01'),
  (${sourceId1_1}, 'file2', 'dir1', 200 , 2222        , 1430316038, 0     , x'aabb02'),
  (${sourceId1_1}, 'file3', 'dir1', 300 , 3333        , 1430316038, 0     , x'aabb03'),
  (${sourceId1_2}, 'dir1' , ''    , 0   , null        , 1430316038, 0     , null     ),
  (${sourceId1_2}, 'file1', 'dir1', 100 , 1111        , 1430316038, 0     , x'aabb01'),
  (${sourceId1_2}, 'file2', 'dir1', 200 , 2222        , 1430316038, 0     , x'aabb02'),
  (${sourceId1_2}, 'file3', 'dir1', 300 , 3333        , 1430316038, 0     , x'aabb03'),
  (${sourceId2_1}, 'file4', ''    , 400 , 4444        , 1430316038, 0     , x'aabb04');

delete from hashes;

insert into hashes
  (main     , position, length      , chunk    , uploaded  )
values
  (x'aabb01', 1       , ${chunkSize}, x'ccdd01', 1430316038),
  (x'aabb01', 2       , ${chunkSize}, x'ccdd02', null      ),
  (x'aabb01', 3       , ${chunkSize}, x'ccdd03', null      ),
  (x'aabb02', 1       , ${chunkSize}, x'ccdd01', 1430316038),
  (x'aabb02', 2       , ${chunkSize}, x'ccdd02', null      ),
  (x'aabb02', 3       , ${chunkSize}, x'ccdd04', null      ),
  (x'aabb03', 1       , ${chunkSize}, x'ccdd05', 1430316038),
  (x'aabb03', 2       , ${chunkSize}, x'ccdd06', 1430316038),
  (x'aabb03', 3       , ${chunkSize}, x'ccdd07', 1430316038),
  (x'aabb03', 4       , ${chunkSize}, x'ccdd08', 1430316038),
  (x'aabb04', 1       , ${chunkSize}, x'ccdd09', null      );
