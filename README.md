Collaborate with front-end developers with no bottle necks!
================

`play2-stub` is a play framework plugin for implementing server stub without any boilerplate coding. With   

`routes`

    GET    /index   contollers.YourController.index
    ...
    GET    /*file   jp.co.bizreach.play2stub.StubController.at(file)


`application.conf`

    play2stub {
      routes: [
        {
          "GET /author/~authorName/books" {
            template = "author-biology"
            data = "authors/:authorName.json"
          }
        }
      ]
    }
    
Currently, `play2-stub` only suppoorts Handlebars templates and depends on [play2-handlebars](https://github.com/bizreach/play2-handlebars) .

Motivation
=====
Sometimes engineers collaborate with front-end engineers or UX designers in one play application. While engineers are implementing eagerly, they want server responses to test their artifacts. Today since clients become richer and richer, they really needs clear responses for both html and ajax response. 
 
UX/UI designers/engineers also want data variation as we engineers do, to test client-side validation behaviors, for layout adjustment and so on. 


Features
=====
- Dynamic parameter passing
- Implicit routing
- Pluggable Processor ...
- Pluggable Filter ...
- Pluggable Template Resolver ...
- Pluggable Param Injector ...
- Pluggable Renderer ...


Getting Started
=====
1. Add a dependency in `Build.scala` or `build.sbt`

    "jp.co.bizreach" %% "play2-stub" % "0.1.0"

  or

    "jp.co.bizreach" %% "play2-stub" % "0.2-SNAPSHOT"

  with the below to refer the Maven's snapshot reository. 
    resolvers +=
      "Maven Central Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"


2. Add a line in `conf/play.plugins`. Usually, add Handlebars plugin declaration also currently.

    1000:jp.co.bizreach.play2handlebars.HandlebarsPlugin
    2000:jp.co.bizreach.play2stub.StubPlugin

3. Add mappings to StubController routes in `routes`. Each http method needs a line. 
 
    GET     /*file         jp.co.bizreach.play2stub.StubController.at(file)
    POST    /*file         jp.co.bizreach.play2stub.StubController.at(file)
    PUT     /*file         jp.co.bizreach.play2stub.StubController.at(file)
    DELETE  /*file         jp.co.bizreach.play2stub.StubController.at(file)


4. Add routes in `application.conf`. The grammar is quite similar to that of Play's route. (But instead of ":", use "~" for path parameters currently).

    play2stub {
      routes: [
        {
          "GET /author/~authorName/books" {
            template = "author-biology"
            data = "authors/:authorName.json"
          }
        }
      ]
    }


5. Then put template files or/and json files and run your application.
 
6. See [src/test/play2-sample1-stub](https://github.com/bizreach/play2-stub/tree/master/src/test/play2-sample1-stub) for more routing samples.


