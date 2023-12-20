killall rmiregistry
sleep 1
cd server/
rmiregistry &
sleep 1
java FrontEnd
java Replica 1
java Replica 2
java Replica 3
java Replica 4
