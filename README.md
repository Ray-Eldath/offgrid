# Offgrid

Next-generation FHE-MPC platform.


## Architecture

 - containerization: docker, docker-compose
 - backend: http4k, Kotlin, AdoptOpenJDK
 - database: MySQL
 - metrics database: Graphite
 - static file server: Nginx
 - OAuth middleware: Hydra
 - edge router: traefik
 - (optional) supervisor: netdata, portainer

## Deployment

We strongly recommend deploy Project Offgrid with our pre-defined `docker-compose.yml`, in this way you can deploy the whole stack with just “one-click” and lots environment variables will be set automatically as well. You may notice that there are two `docker-compose` files in the root folder, `docker-compose.yml` and `docker-compose.supervision.yml`. The former is contains everything consisting Project Offgrid, the latter is some supervision components which is not directly related to Project Offgrid, it is fine to omit it. Though not necessary, we still recommend both `docker-compose` file to be deployed.

### Prerequisites

`docker` and `docker-compose` should be installed in advance, and the project should be cloned: 

```shell script
$ git clone http://github.com/Ray-Eldath/offgrid
$ git clone http://github.com/Ray-Eldath/ongrid
```

Build front-end project following the instruction in `../ongrid/README.md`.

Make sure the tree structure is like:

```
[parent directory]
└-- ongrid
   └-- dist
   └-- Dockerfile
└-- offgrid
   └-- build
      └-- libs
         └-- offgrid*all.jar
   └-- Dockerfile
   └-- docker-compse.yml
   └-- docker-compose-supervision.yml
```

### Environment variables

Some password and secret can be generated with `cat /dev/urandom | tr -dc 'a-zA-Z0-9' | fold -w 32 | head -n 1`. We believe such length (32) is enough for a typical application.

Note that all URL-staff should omit the tailing slash mark (`/`).

| Environment variable                          | Description                                                  | Example                            |
| --------------------------------------------- | ------------------------------------------------------------ | ---------------------------------- |
| `OFFGRID_HOST`                                | Host of the front-end (ongrid) entrypoint.                 | `https://offgrid.org`              |
|`OFFGRID_DOMAIN`  |Domain of the full stack, should be part of `OFFGRID_HOST`.|`offgrid.org`|
| `OFFGRID_DATABASE_PASSWORD`                   | Password of MySQL user `offgrid`. During the initialization process, a user `offgrid` with this password as well as a schema named `offgrid` will be created. All privileges and table creation will be handled automatically. | N/A |
| `OFFGRID_DATABASE_ROOT_PASSWORD`              | Password of MySQL user `root`, will used by Hydra. Should be kept carefully. | N/A                                |
| `OFFGRID_HYDRA_SECRETS_SYSTEM`                | Secret used to encrypt database of Hydra, **at least 16 length is required. ** Should be kept carefully. | N/A                             |
| `OFFGRID_ALIYUN_DIRECTMAIL_ACCESS_KEY_ID`     | `AccessKeyId` of Aliyun DirectMail service.                  | N/A                                |
| `OFFGRID_ALIYUN_DIRECTMAIL_ACCESS_KEY_SECRET` | `AccessKeySecret` of Aliyun DirectMail service.              | N/A                                |
| `OFFGRID_GRAFANA_HOST`                        | Host of Grafana panel public service. **Should in HTTPS protocol**, otherwise automatically initialization of Hydra will failed. | `https://grafana.offgrid.org`      |
| `OFFGRID_GRAFANA_OAUTH_CLIENT_SECRET` | Secret of OAuth client of Grafana, will be registered in Hydra. | N/A |
| `OFFGRID_TRAEFIK_HASHED_PASSWORD`             | **Hashed** Password of Traefik panel, **should be generated with the procedure described following.** The panel is protected by basic authorization with user `traefik` and this password. | N/A                                |
|`OFFGRID_ACME_EMAIL`|Used for obtain HTTPS certificate from Let’s Encrypt with ACME protocol. Default LTS Challenge is used.|`alpha.beta@omega`|

Many environment variables are handled by `docker-compose` automatically since inter-containers connection could established use container name solely, these environment variables are not listed above. So if you deploy Project Offgrid without the pre-defined `docker-compose.yml`, these unset variables may cause problems.

### Deploy the whole stack with "one click"

After all required environment variables are set, run:

```shell script
$ cd offgrid

# now set environment variables...
$ export ...=...

# subsitute <TRAEFIK_PASSWORD> with the appropriate password you want to protect the traefik panel
$ export OFFGRID_TRAEFIK_HASHED_PASSWORD=$(echo $(htpasswd -nb traefik <TRAEFIK_PASSWORD>) | sed -e s/\\$/\\$\\$/g)

# set data directory writable
$ mkdir ~/offgrid
$ chmod a+rwx -R ~/offgrid

# after set all environment variables
# if supervisor is not needed:
$ docker-compose up -d

# if supervisor should be deployed: 
$ docker-compose -f docker-compose.yml -f docker-compose.supervision.yml
```

It may takes 1-2 minutes for all service to spin up as well as the resource usage turns stable, only after can the entrypoints accessible.

### Entrypoints

Panels and static file server will be exposed to the Internet using edge router with certain entrypoints:

| Entrypoints (`domain.com` as host name) | Service                                             |
| --------------------------------------- | --------------------------------------------------- |
| `https://domain.com`                    | `frontend`                                          |
| `https://api.domain.com/`               | `backend`                                           |
| `https://domain.com/service/oauth`      | `hydra` (public API only)                           |
| `https://grafana.domain.com/`           | `grafana`                                           |
| `https://netdata.domain.com/`           | `netdata` (if deployed)                             |
| `https://portainer.domain.com/`         | `portainer` (if deployed)                           |
| `https://traefik.domain.com:8080/`      | `traefik` (panel, protected by basic authorization) |

Services not listed above indicates them are not exposed to the Internet. 

Such entrypoints are handled by edge router with service discovery. So if the correspond service is stopped, the related entrypoint will be withdrawn and become invisible while the remaining services still exposed to the Internet.

## Disclaimer

Project Offgrid is developed by *IllegalSkillsException*. Most code-related components is developed by [Ray Eldath](https://github.com/Ray-Eldath)).

Does not guarantee availability, usability, safety as well as maintenance.