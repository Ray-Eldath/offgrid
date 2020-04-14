create schema hydra;
create schema grafana;

#
use offgrid;

create table entities
(
    id                   char(36)     not null
        primary key,
    name                 varchar(20)  null,
    type                 int          not null,
    access_key_id        char(36)     not null,
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
    from_id char(36)      not null,
    to_id   char(36)      not null,
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
    entity_id char(36)    null,
    tag       varchar(20) null,
    constraint entity_tags_entities_id_fk
        foreign key (entity_id) references entities (id)
            on delete cascade
);

create table user_applications
(
    id                       int auto_increment
        primary key,
    email                    varchar(50)          not null,
    is_email_confirmed       tinyint(1) default 0 not null,
    email_confirmation_token char(36)             not null,
    last_request_token_time  datetime             not null,
    hashed_password          char(97)             null,
    username                 varchar(30)          null,
    is_application_pending   tinyint(1) default 1 not null,
    constraint User_Applications_email_uindex
        unique (email)
);

create table users
(
    id              int auto_increment
        primary key,
    state           int default 0 not null,
    email           varchar(50)   not null,
    username        varchar(30)   not null,
    hashed_password char(97)      not null,
    role            int           not null,
    last_login_time datetime      null,
    register_time   datetime      not null,
    constraint Users_email_unique
        unique (email)
);

create table extra_permissions
(
    user_id       int                  not null,
    permission_id varchar(5)           not null,
    is_shield     tinyint(1) default 1 not null,
    constraint extra_permissions_users_id_fk
        foreign key (user_id) references users (id)
            on delete cascade
);

create index extra_permissions_user_id_index
    on extra_permissions (user_id);

create table reset_password_applications
(
    user_id      int auto_increment
        primary key,
    email        varchar(50) not null,
    token        varchar(50) not null,
    request_time datetime    not null,
    constraint reset_password_applications_users_id_fk
        foreign key (user_id) references users (id)
            on delete cascade
);

alter table users
    auto_increment = 1000;

alter table user_applications
    auto_increment = 1000;

alter table entity_routes
    auto_increment = 1000;

alter table entity_tags
    auto_increment = 1000;