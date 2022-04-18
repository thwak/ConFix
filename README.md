# ConFix
ConFix - Automated Patch Generation with Context-based Change Application   

For detailed approaches and evaluation, please check this [manuscript](https://github.com/thwak/ConFix/wiki/pre-print.pdf).  

Currently, ConFix is fitted to execute for Defects4j bugs.
To run ConFix, you'll need the followings.  

`confix.properties` for paths and configuration.  
Test lists for cascade candidate verification. ConFix only executes the next list if all in the previous are passed. 
`tests.trigger` - a list of trigger tests.   
`tests.relevent` - a list of relevent tests.  
`tests.all` - the full list.  
`coverage-info.obj` - an object file storing `CoverageManager` for coverage information. 
 
You can find samples of these file in `samples`.  
For `coverage-info.obj`, you can create `CoverageManager` instance and store it with `IOUtils.storeObject()`. 

ConFix also requires a change pool.   
PTLRH and PLRT change pools obtained from 9 open source projects can be found [here](https://github.com/thwak/confix2019result).  

During execution, ConFix may generate the following files.

`coveredlines.txt` - a list of lines considered for modification in current configuration.  
`lines-{pool}.txt` - a list of fix location candidates examined with {pool}.  
`locinfo.csv` - Information of checked location/change numbers.  
`patch_info` - Information of generated plausible patches.  
`patches` - Containing generated plausible patches. 
`candidates` - a temp. dir to store current candidate code.  
`tmp` - a temp. dir for compilation and test execution. 
