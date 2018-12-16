--> clientName
select 'My PC';

delete from sources;

--> sourceName1
select 'source1';

--> sourceName2
select 'source2';

insert into sources
  (client       , name          , rootPath             )
values
  (${clientName}, ${sourceName1}, '/' || ${sourceName1}),
  (${clientName}, ${sourceName2}, '/' || ${sourceName2});

--> sourceId1
select id from sources where name = ${sourceName1} and client = ${clientName};

--> sourceId2
select id from sources where name = ${sourceName2} and client = ${clientName};

delete from files;

insert into files
  (sourceId    , name, parent   , status)
values
  (${sourceId2}, '2' , '/path/1', 0      ),
  (${sourceId1}, '1' , '/path/1', 0      ),
  (${sourceId2}, '2' , '/path/2', 0      ),
  (${sourceId1}, '1' , '/path/2', 0      );
