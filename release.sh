#!/usr/bin/env bash
export JAVA_HOME=`/usr/libexec/java_home -v 1.8`
export PATH=JAVA_HOME/bin:$PATH

read -p "Really deploy to Maven Central repository (Y/N)? "
if ( [ "$REPLY" == "Y" ] ) then

  mvn clean
  mvn release:clean release:prepare release:perform -Prelease -e | tee release.log

else
  echo -e "Exit without deploy"
fi
