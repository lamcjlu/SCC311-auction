killall rmiregistry
sleep 1
cd server/
rmiregistry &
sleep 1
java Server
