# coding: utf-8
from os import path as os_path
from re import compile as re_compile

cwd = re_compile(".*\/").search(os_path.realpath(__file__)).group(0)

parameters = {
       "port" : 10000,
       "sslport" : -1,
       "host" : "0.0.0.0",
       "keepsession" : True,
#       "keepsession" : False,
       "debug_host" : False,
#       "test" : cwd + "logs/",
       "log" : False,
       "tlog" : False,
       "cert" : cwd + "mica/mica.crt",
       "privkey" : False,
       "slaves" : False,
       "slave_port" : False,
       "scratch" : cwd + "mica/",
       "duplicate_logger" : False,
       "couch_adapter_type" : "AndroidMicaServerCouchbaseMobile",
       "transreset" : False,
       "transcheck" : False,
       "mobileinternet" : False,

       "main_server" : "readalien.com",
       "couch_server" : "db.readalien.com",
       "couch_proto" : "https",
       "couch_port" : "443",

       "trans_id" : False,
       "trans_secret" : False,

        # Only used during development by uncommenting
        # a hard-coded HTTP listener for debugging purposes.
        # couchdb listener is not enabled in the app store
       "local_database" : "mica",
#           "local_username" : "admin",
#           "local_password" : "devtest",
       "local_username" : False,
       "local_password" : False,
       "local_port" : 5984,
}
