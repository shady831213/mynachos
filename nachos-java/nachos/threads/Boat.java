package nachos.threads;

import nachos.ag.BoatGrader;
import nachos.machine.Lib;

public class Boat {
    static BoatGrader bg;
    private static final int noOneOnBoat = 0;
    private static final int oneChildOnBoat = 1;
    private static final int twoChildrenOnBoat = 2;
    private static final int oneAdultOnBoat = 3;

    private static station Oahu, Molokai;
    private static boat boatObject;

    static class station {
        public int getAdults() {
            return adults;
        }

        public void addAdults(int adults) {
            this.adults += adults;
        }

        public void subAdults(int adults) {
            this.adults -= adults;
            Lib.assertTrue(this.adults >= 0);
        }

        public int getChildren() {
            return children;
        }

        public void addChildren(int children) {
            this.children += children;
        }

        public void subChildren(int children) {
            this.children -= children;
            Lib.assertTrue(this.children >= 0);
        }

        public boolean noOne() {
            return adults == 0 && children == 0;
        }


        private int adults;
        private int children;

        station(int adults, int children) {
            this.adults = adults;
            this.children = children;
        }

    }

    static class boat {
        public int getBoatStatus() {
            return boatStatus;
        }

        private int boatStatus;

        final private Lock lock = new Lock();
        final private Condition2 boatOnDepartureCondition = new Condition2(lock),
                boatOnTerminationCondition = new Condition2(lock);

        private station departure, termination, curStation;


        boat(station departure, station Termination) {
            boatStatus = noOneOnBoat;
            this.departure = departure;
            this.termination = Termination;
            this.curStation = departure;
        }

        private void takeOff() {
            if (boatStatus == oneAdultOnBoat) {
                this.curStation.addAdults(1);
            } else {
                this.curStation.addChildren(boatStatus);
            }
            boatStatus = 0;
        }

        private void childOnBoard() {
            this.curStation.subChildren(1);
            boatStatus++;
            Lib.assertTrue(boatStatus <= twoChildrenOnBoat);
        }

        private void adultOnBoard() {
            this.curStation.subAdults(1);
            boatStatus = oneAdultOnBoat;
        }


        public void adultOnBoardOnDeparture() {
            lock.acquire();
            while (curStation == termination || boatStatus > noOneOnBoat) {
                boatOnDepartureCondition.sleep();
            }
            adultOnBoard();
        }

        public void childOnBoardOnDeparture() {
            lock.acquire();
            //when there is 1 children on departure, adults first!
            while (curStation == termination || boatStatus >= twoChildrenOnBoat || departure.getChildren() == 1 && boatStatus == noOneOnBoat) {
                boatOnDepartureCondition.sleep();
            }
            childOnBoard();
        }


        public void takeOffToTermination() {
            Lib.assertTrue(lock.isHeldByCurrentThread());
            if (departure.noOne() || boatStatus >= twoChildrenOnBoat) {
                this.curStation = this.termination;
                takeOff();
                boatOnTerminationCondition.wakeAll();
            }
            lock.release();
        }

        public boolean childOnBoardOnTermination() {
            boolean needReturn;
            lock.acquire();
            while (curStation == departure || boatStatus != noOneOnBoat) {
                boatOnTerminationCondition.sleep();
            }
            needReturn = !this.departure.noOne();
            if (!needReturn) {
                lock.release();
            } else {
                childOnBoard();
            }
            return needReturn;
        }

        public void childTakeOffToDeparture() {
            Lib.assertTrue(lock.isHeldByCurrentThread());
            this.curStation = this.departure;
            takeOff();
            boatOnDepartureCondition.wakeAll();
            lock.release();
        }

    }

    public static void selfTest() {
        BoatGrader b = new BoatGrader();

        System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
        begin(1, 2, b);

        System.out.println("\n ***Testing Boats with only 2 children***");
        begin(0, 2, b);

        System.out.println("\n ***Testing Boats with only 5 children***");
        begin(0, 5, b);

        System.out.println("\n ***Testing Boats with only 1 adult***");
        begin(1, 0, b);

        System.out.println("\n ***Testing Boats with no one***");
        begin(0, 0, b);


        System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
        begin(3, 3, b);

        //boat random test
        if (Lib.test('B') && Lib.test('R')) {
            for (int i = 0; i < 10; i++) {
                int adults_random = Lib.random(100);
                int children_random = Lib.random(100) + 2;
                System.out.println("\n ***Random Testing Boats " + i + " with " + children_random + " children, " + adults_random + " adults***");
                begin(adults_random, children_random, b);
            }
        }
    }

    public static void begin(int adults, int children, BoatGrader b) {
        // Store the externally generated autograder in a class
        // variable to be accessible by children.
        bg = b;

        // Instantiate global variables here
        Oahu = new station(adults, children);
        Molokai = new station(0, 0);
        boatObject = new boat(Oahu, Molokai);

        // Create threads here. See section 3.4 of the Nachos for Java
        // Walkthrough linked from the projects page.
        Runnable childrenJobs[] = new Runnable[children];
        KThread childrenThreads[] = new KThread[children];
        Runnable adultsJobs[] = new Runnable[adults];
        KThread adultsThreads[] = new KThread[adults];

        for (int i = 0; i < children; i++) {
            childrenJobs[i] = new Runnable() {
                public void run() {
                    ChildItinerary();
                }
            };
            childrenThreads[i] = new KThread(childrenJobs[i]);
            childrenThreads[i].setName("child_" + i);
        }

        for (int i = 0; i < adults; i++) {
            adultsJobs[i] = new Runnable() {
                public void run() {
                    AdultItinerary();
                }
            };
            adultsThreads[i] = new KThread(adultsJobs[i]);
            adultsThreads[i].setName("adult_" + i);
        }
        for (int i = 0; i < children; i++) {
            childrenThreads[i].fork();
        }
        for (int i = 0; i < adults; i++) {
            adultsThreads[i].fork();
        }
        for (int i = 0; i < children; i++) {
            childrenThreads[i].join();
        }
        for (int i = 0; i < adults; i++) {
            adultsThreads[i].join();
        }
        Lib.assertTrue(Oahu.getAdults() == 0);
        Lib.assertTrue(Oahu.getChildren() == 0);
        Lib.assertTrue(Molokai.getAdults() == adults);
        Lib.assertTrue(Molokai.getChildren() == children);
    }

    static void AdultItinerary() {
	/* This is where you should put your solutions. Make calls
	   to the BoatGrader to show that it is synchronized. For
	   example:
	       bg.AdultRowToMolokai();
	   indicates that an adult has rowed the boat across to Molokai
	*/
        //waiting boat to arrive Oahu
        boatObject.adultOnBoardOnDeparture();
        //from Oahu to Molokai
        bg.AdultRowToMolokai();
        boatObject.takeOffToTermination();
    }

    static void ChildItinerary() {
        while (true) {
            //waiting boat to arrive Oahu
            boatObject.childOnBoardOnDeparture();
            //from Oahu to Molokai
            if (boatObject.getBoatStatus() == oneChildOnBoat) {
                bg.ChildRowToMolokai();
            } else {
                bg.ChildRideToMolokai();
            }
            boatObject.takeOffToTermination();
            //waiting boat to arrive Molokai
            if (!boatObject.childOnBoardOnTermination()) {
                break;
            }
            //from Oahu to Molokai, only allowed one child
            bg.ChildRowToOahu();
            boatObject.childTakeOffToDeparture();
        }
    }

    static void SampleItinerary() {
        // Please note that this isn't a valid solution (you can't fit
        // all of them on the boat). Please also note that you may not
        // have a single thread calculate a solution and then just play
        // it back at the autograder -- you will be caught.
        System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
        bg.AdultRowToMolokai();
        bg.ChildRideToMolokai();
        bg.AdultRideToMolokai();
        bg.ChildRideToMolokai();
    }

}
