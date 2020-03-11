create schema hydra;
create schema grafana;

#
use offgrid;

create table user_applications
(
    id                       int auto_increment
        primary key,
    email                    varchar(50)          not null,
    is_email_confirmed       tinyint(1) default 0 not null,
    email_confirmation_token varchar(50)          not null,
    last_request_token_time  datetime             not null,
    hashed_password          varchar(150)         null,
    username                 varchar(20)          null,
    is_application_pending   tinyint(1) default 1 not null,
    constraint User_Applications_email_uindex
        unique (email)
);

create table users
(
    id       int auto_increment
        primary key,
    state    int default 0 not null,
    username varchar(20)   not null,
    email    varchar(50)   not null,
    constraint Users_email_unique
        unique (email)
);

create table authorizations
(
    user_id         int          not null
        primary key,
    hashed_password varchar(150) not null,
    role            int          not null,
    last_login_time datetime     null,
    register_time   datetime     null,
    constraint fk_Authorizations_user_id_id
        foreign key (user_id) references users (id)
            on delete cascade
);

create table extra_permissions
(
    authorization_id int                  not null,
    permission_id    varchar(5)           not null,
    is_shield        tinyint(1) default 1 not null,
    constraint Extra_Permissions_Authorizations_user_id_fk
        foreign key (authorization_id) references authorizations (user_id)
            on delete cascade
);

create table reset_password_applications
(
    id               int auto_increment
        primary key,
    authorization_id int         not null,
    email            varchar(50) not null,
    token            varchar(50) not null,
    request_time     datetime    not null,
    constraint Reset_Passwords_Authorizations_user_id_fk
        foreign key (authorization_id) references authorizations (user_id)
);

create table entities
(
    id                   int auto_increment
        primary key,
    name                 varchar(20)  null,
    type                 int          not null,
    access_key_id        varchar(50)  not null,
    access_key_secret    varchar(150) null,
    create_time          datetime     not null,
    last_connection_time datetime     null,
    constraint entities_access_id_uindex
        unique (access_key_id),
    constraint entities_name_uindex
        unique (name)
);

create table entity_routes
(
    id      int auto_increment
        primary key,
    state   int default 0 not null,
    from_id int           not null,
    to_id   int           not null,
    constraint entity_routes_entities_id_fk
        foreign key (from_id) references entities (id)
            on delete cascade,
    constraint entity_routes_entities_id_fk_2
        foreign key (to_id) references entities (id)
            on delete cascade
);

create table entity_tags
(
    id        int auto_increment
        primary key,
    entity_id int         null,
    tag       varchar(20) null,
    constraint entity_tags_entities_id_fk
        foreign key (entity_id) references entities (id)
            on delete cascade
);

alter table users
    auto_increment = 1000;

alter table user_applications
    auto_increment = 1000;

alter table entities
    auto_increment = 1000;

alter table entity_routes
    auto_increment = 1000;

alter table entity_tags
    auto_increment = 1000;