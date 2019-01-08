# mynachos
Berkeley CS162 nachos assignment

## Vedio
[cs162_2010](https://www.bilibili.com/video/av17833855/)

## Materials
[cs162_project_materials](https://people.eecs.berkeley.edu/~kubitron/cs162/)

# Solutions
## Start
```
cd nachos-java/nachos
source  setenv.sh
cd test
make
cd ../proj[1-4]
make
nachos
```

## Test framework
Add *testsuite* and *testcase* in Lib.java. 
example:
``` java
        Lib.TestSuite ts = new Lib.TestSuite();

        //xxx test
        ts.addTest(new Lib.Test("xxx_test", new Runnable() {
            @Override
            public void run() {
            //...
                //check exit status
                Lib.assertTrue(...);
            }
        }));
        
        //yyy test
        ts.addTest(new Lib.Test("yyy_test", new Runnable() {
            @Override
            public void run() {
            //...
                //check exit status
                Lib.assertTrue(...);
            }
        }));
        //fire!
        ts.run();
```

When *selfTest* is running, it can get test result:
```
Test: xxx_test begin...
Test: xxx_test Pass!
Test: yyy_test begin...
Test: yyy_test Pass!
```

## Cross-compile tool chain
How to build the tool chain refer to [this page](https://inst.eecs.berkeley.edu/~cs162/fa13/)

## About proj3
*MemMap* is used for allocing page and maintain a *inverted page table*. The *inverted page table* is a *Page* type array indexed by ppn. *Page* can mapped to a specific *AddressMapping* implementation. All user process maintain an *AddressMapping* table indexed by vpn.

## About proj4
Modified protocal described in [here](https://people.eecs.berkeley.edu/~kubitron/cs162/Nachos/net-proto/spec.html).The difference is closing handshakes which is implemented by 4-steps instead of 3-steps. And *State Pattern* is used to implement the protocal.