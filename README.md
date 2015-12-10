Jenkins Guice plugin
====================

The main goal of this plugin is to enable clean Dependency Injection for Jenkins plugins.

Important notice
----------------

This plugin is in beta testing phase. There could be bugs.

Why Dependency Injection?
-------------------------

[Dependency Injection pattern] (https://en.wikipedia.org/wiki/Dependency_injection) can help in making your plugin code
cleaner and better structured. It allows extension classes to be loosely coupled and also simplify unit testing of
plugin code.

When do I need this plugin?
---------------------------

This plugin is required when you have non-transient fields in your extension classes, which is common situation for
Jenkins plugins. If you don't have any non-transient fields in your plugin you can go ahead and just use vanilla
Jenkins without this plugin. For the rest of cases, Guice plugin provides XStream converter, that is aware of
Dependency Injection context and can handle marshalling / unmarshalling from XML.

How does it work?
-----------------

Guice plugin is based on [Assisted Inject](https://github.com/google/guice/wiki/AssistedInject). As an addition to
plain Guice feature this plugin comes with 2 new annotations:

* @AssistedFactory
* @AssistedGetter

See examples below to find how to use them.

How to enable Dependency Injection for your Jenkins plugin
----------------------------------------------------------

### 1. Import Guice plugin

Add new dependency to your Maven pom.xml:

    <dependency>
        <groupId>org.jenkins-ci.plugins</groupId>
        <artifactId>guice-plugin</artifactId>
        <version>0.1-SNAPSHOT</version>
    </dependency>

### 2. Create Guice module

Implement interface com.google.inject.Module and mark it as extension:

    @Extension
    public class HelloWorldPluginModule implements Module {
        @Override
        public void configure(Binder binder) {
            //Bind each extension descriptor as eager singleton
            binder.bind(HelloWorldBuilder.DescriptorImpl.class).asEagerSingleton();
        }
    }
    
### 3. For each extension with non-transient fields

Create factory interface and annotate each parameter with @Assisted annotation:

    public interface HelloWorldBuilderFactory {
        HelloWorldBuilder create(@Assisted("username") String username);
    }

### 4. Bind each factory
            
    public class HelloWorldPluginModule implements Module {
        @Override
        public void configure(Binder binder) {
            //Bind each extension descriptor as eager singleton
            binder.bind(HelloWorldBuilder.DescriptorImpl.class).asEagerSingleton();
            
            //Bind each factory using FactoryModuleBuilder
            binder.install(new FactoryModuleBuilder()
                .implement(HelloWorldBuilder.class, HelloWorldBuilder.class)
                .build(HelloWorldBuilderFactory.class)
            );
        }
    }
    
### 5. Add annotations to the extension class
    
    //Add converter so that Guice can properly unmarshall your extension from XML configuration.
    @XStreamConverter(DependencyInjectionConverter.class)
    //Link factory interface
    @AssistedFactory(HelloWorldBuilderFactory.class)
    public class HelloWorldBuilder extends Builder {
    
    	private final transient HelloService helloService;
    	private final String username;
    	
    	//Annotate constructor
    	@Inject
    	public HelloWorldBuilder(HelloService helloService, @Assisted("username") String username){
    	    this.helloService = helloService; //service is injected from context
    	    this.username = username; //username is defined in XML or in factory method call
    	}
    	
    	//Annotate getter for username
    	@AssistedGetter("username")
    	public String getUsername(){
    	    return this.username;
    	}
    }
    
### 6. Enjoy Dependency Injection :-)

Now your extension dependencies are injected from Guice context. You can also obtain new instance of extension manually.
In order to do that inject factory class and call factory method:

    public class AnotherService {
    
        private final HelloWorldBuilder helloWorldBuilder;
        
        @Inject
        public AnotherService(HelloWorldBuilderFactory helloWorldBuilderFactory(){
            this.helloWorldBuilder = helloWorldBuilderFactory.create("Alex");
        }   
    
    }
    
Limitation
----------

At this moment plugin does not support @DataBoundConstructor. As a workaround you can override newInstance
method in descriptor:

    @Override
    public HelloWorldBuilder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
        return helloWorldBuilderFactory.create(formData.getString("username"));
    }
    
TO-DO List
----------

1. Cover with unit tests
2. Implement proper validation of factory method parameters
3. Check whether it's possible to register DependencyInjectionConverter without explicit annotation on each extension
4. Auto-detect getters and factories
5. Support for DataBoundConstructor

How to contribute to this plugin
--------------------------------

Any help is highly appreciated. If you want to push some changes - please create a pull request. If you want to help
with maintaining this plugin - please drop an email to current maintainers.

See [Contributing to existing plugin]
(https://wiki.jenkins-ci.org/display/JENKINS/Governance+Document#GovernanceDocument-Makingchangestoexistingplugins)

License
-------

Jenkins Guice plugin source code and binaries are released under the Apache License 2.0.
