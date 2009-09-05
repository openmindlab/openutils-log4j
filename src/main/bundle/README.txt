===========================================================

${pom.name} ${pom.version}
by ${pom.organization.name}

===========================================================

${pom.description}

More information about this project can be found at:
${pom.url}

Change log and known issues can be found at:
${pom.issueManagement.url}

Source is available from the subversion repository at:
${pom.scm.url}

=========================================
 USAGE
=========================================

The preferred way to add ${pom.name} to your project is using maven.
You can declare the following dependency in your pom.xml:

    <dependency>
      <groupId>${pom.groupId}</groupId>
      <artifactId>${pom.artifactId}</artifactId>
      <version>${pom.version}</version>
    </dependency>

Alternatively, you can add the ${pom.artifactId} plus the required dependencies
manually to the classpath of your application.


=========================================
 LICENSE
=========================================

Copyright Openmind http://www.openmindonline.it

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

