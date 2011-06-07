# DateEmbargoAttributeFinderModule for Fedora 3.5 #
This project is an example of the Spring-configured components introduced in Fedora 3.5.  It is an implementation of the [org.fcrepo.server.security.AttributeFinderModule](https://github.com/fcrepo/fcrepo/blob/master/fcrepo-server/src/main/java/org/fcrepo/server/security/AttributeFinderModule.java) interface, which is the interface to code providing Attributes to inform XACML policy decisions.

To use this module, you will need to:
1. build the jar (mvn package should work)
2. copy the jar into Fedora 3.5's webapp library ($CATALINA_HOME/webapps/fedora/WEB-INF/lib, for example)
3. wire an instance of the DateEmbargoAttributeFinderModule into the PolicyEnforcementPoint with Spring configuration
4. create a XACML policy that operates on the attribute it produces
5. create object properties or relationships that indicate an embargo-until date

## Spring Configuration ##
Under $FEDORA_HOME/server/config/spring, there will be a file containing the PolicyEnforcementPoint configuration- it's called policy-enforcement.xml by default.  You will need to make two changes to this configuration.  The first is to add a bean definition for the DaeEmbargoAttributeFinderModule:

        <bean id="org.fcrepo.server.security.DateEmbargoAttributeFinderModule"
              class="org.fcrepo.server.security.DateEmbargoAttributeFinderModule"
              autowire-candidate="true">
              <!--  The first constructor arg is a string value indicating the uri
              of the object property or RELS-EXT/INT predicate that indicates the
               embargo-until date -->
              <constructor-arg type="java.lang.String"
               index="0" value="info:fedora/demo/properties/embargountil" />
               <!-- The second constructor arg is an enum that indicates
                whether the embargo-until date is encoded in a RELS-EXT or RELS-INT
                property (OBJECT_RELS) or in an object extension property (OBJECT_PROPS)-->
              <constructor-arg type="org.fcrepo.server.security.EmbargoDatePropertyType"
               index="1" name="type" value="OBJECT_RELS" />
               <!-- The third constructor arg is the SimpleDateFormat pattern that
                should be used to parse the embargo-until date -->
              <constructor-arg type="java.lang.String"
               index="2" value="yyyyMMdd" />
               <!-- This property wires in the DOManager implementaton. -->
              <property name="DOManager">
                  <ref bean="org.fcrepo.server.storage.DOManager" />
              </property>
        </bean>

The second is to find the PolicyEnforcementPoint bean and wire your new module into it:

        <bean id="org.fcrepo.security.PolicyEnforcementPoint" ...>
          <property name="attributeFinderModules">
            <list>
              <ref bean="org.fcrepo.server.security.ResourceAttributeFinderModule" />
              <ref bean="org.fcrepo.server.security.ContextAttributeFinderModule" />
              <!-- adding new module below -->
              <ref bean="org.fcrepo.server.security.DateEmbargoAttributeFinderModule" />
            </list>
          </property>
        </bean>

## XACML Policies ##
This module looks at Fedora objects for the property or relationship indicated in the module's Spring configuration, and parses it into a date with SimpleDateFormat.  It then compares that date to the time of access.
If the time of access is later than the date indicated on the object, the module will return a String value of "false" for the Resource Attribute named **urn:fedora:names:fedora:2.1:resource:embargoed**.  If the access time is before that date, the value for **urn:fedora:names:fedora:2.1:resource:embargoed** wil be "true". There is an example policy acting on this Attribute in this project, under src/test/resources.

## Object Properties ##
You may indicate the embargo-until date on an object in two ways: In an object-extension property, or a relationship.  The module will decide where to look based on a constructor argument to the module documented in the example Spring configuration above.
If the configured predicate appears in both RELS-EXT and RELS-INT triples in the same object, the module will use the most specific triple to the request- i.e., if the request has a datastream id in context, it will attempt to use RELS-INT and fall back to RELS-EXT.  If there are multiple triples with the same level of relevance, it will assume the latest indicated date.

An example object with RELS-INT properties is available in this project, under src/test/resources.

## More on Spring in Fedora 3.5 ##
For more information on Spring-configured components in Fedora 3.5, please see https://wiki.duraspace.org/display/FEDORADEV/Spring+Configuration
