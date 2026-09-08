create table if not exists vendors (
    id varchar(100) primary key,
    name varchar(200) not null,
    description varchar(1000) not null
);

create table if not exists response_cache (
    cache_key varchar(1000) primary key,
    response clob not null,
    updated_at timestamp not null
);
