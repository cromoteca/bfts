--> clientName
select 'My PC';

delete from sources;

--> sourceName
select 'source1';

insert into sources (client, name, rootPath)
          values (${clientName}, ${sourceName}, '/' || ${sourceName});

--> sourceId
select id from sources where name = ${sourceName} and client = ${clientName};

delete from files;

insert into files
  (sourceId   , name   , parent, size, lastModified, created   , status)
values
  (${sourceId}, 'dir1' , ''    , 0   , null        , 1430316038, 0     ),
  (${sourceId}, 'file1', 'dir1', 100 , 1111        , 1430316038, 0     ),
  (${sourceId}, 'file2', 'dir1', 200 , 2222        , 1430316038, 0     ),
  (${sourceId}, 'file3', 'dir1', 300 , 3333        , 1430316038, 0     );
