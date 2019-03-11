# ConFix
ConFix - Automated Patch Generation with Context-based Change Application

Currently, ConFix is fitted to execute for Defects4j bugs.
To run ConFix, you'll need the followings.  

`confix.properties` for paths and configuration.  
Test lists for cascade candidate verification.  
`tests.trigger` - a list of trigger tests.   
`tests.relevent` - a list of relevent tests.  
`tests.all` - the full list.  
`coverage-info.obj` - an object file storing `CoverageManager` for coverage information. 
 
You can find samples of these file in `samples`.  
For `coverage-info.obj`, you can create `CoverageManager` instance and store it with `IOUtils.storeObject()`. 

ConFix also requires a change pool.   
PTLRH and PLRT change pools obtained from 9 open source projects can be found [here](https://github.com/thwak/confix2019result).  
