# I2PChatClient

## Original Readme
this software comes from invisibleman

there is a forum thread about it
http://forum.i2p/viewtopic.php?t=5271

I'm not writing up a doc for him atm, but feel free to ask me in irc2p #i2p-chat about it

## How to compile

- Copy and unzip the following files here: i2p.jar, mstreaming.jar, streaming.jar
- Compile the Java files: javac ChatClient.java

## How to create a jar

- jar cvfe ChatClient.jar ChatClient *.class net/i2p/data/DataFormatException.class net/i2p/I2PException.class && java -jar ChatClient.jar 

