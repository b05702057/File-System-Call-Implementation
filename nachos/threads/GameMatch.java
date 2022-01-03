package nachos.threads;

import nachos.machine.*;

import java.util.*;

/**
 * A <i>GameMatch</i> groups together player threads of the same
 * ability into fixed-sized groups to play matches with each other.
 * Implement the class <i>GameMatch</i> using <i>Lock</i> and
 * <i>Condition</i> to synchronize player threads into groups.
 */
public class GameMatch {
    
    /* Three levels of player ability. */
    public static final int abilityBeginner = 1,
	abilityIntermediate = 2,
	abilityExpert = 3;
    
    /*Additional Constants*/
    // The final keyword indicates that the value cannot be modified.
    private static final int abilityNum = 3;     

    /* Instance Variables */
    private int globalMatchNumber;  // the match number to be granted to the next match created
    private Lock matchNumberLock;  // lock on the match number to avoid the race condition
    private int matchSize;  // object specific constant instantiated at GameMatch object creation
    private ArrayList< ArrayList<Integer> > matchNumbers; // a 2d ArrayList to record the match numbers for beginners, intermediate players, and experts

    private Lock[] matchLocks;  // the CV lock for each match
    private Condition[] matchConditions;  // the condition variables for each match
    private int[] matchSizes;  // the number of players waiting on each match
    private int[] playerIndexes; // the playerIndex of each ability

    /**
     * Allocate a new GameMatch specifying the number of player
     * threads of the same ability required to form a match.  Your
     * implementation may assume this number is always greater than zero.
     */
    public GameMatch (int numPlayersInMatch) {
    	this.globalMatchNumber = 1;
        this.matchNumberLock = new Lock();
        this.matchSize = numPlayersInMatch;
        this.matchNumbers = new ArrayList< ArrayList<Integer> > ();
        
        // variables to instantiate for each ability
        this.matchLocks = new Lock[abilityNum];
        this.matchConditions = new Condition[abilityNum];
        this.matchSizes = new int[abilityNum];
        this.playerIndexes = new int[abilityNum];
        
        for (int i = 0; i < abilityNum; i++){
            this.matchLocks[i] = new Lock();
            this.matchConditions[i] = new Condition(this.matchLocks[i]);
            this.matchSizes[i] = 0;
            this.playerIndexes[i] = 0;
            this.matchNumbers.add(new ArrayList<Integer> ());
        }
    }

    /**
     * Wait for the required number of player threads of the same
     * ability to form a game match, and only return when a game match
     * is formed.  Many matches may be formed over time, but any one
     * player thread can be assigned to only one match.
     *
     * Returns the match number of the formed match.  The first match
     * returned has match number 1, and every subsequent match
     * increments the match number by one, independent of ability.  No
     * two matches should have the same match number, match numbers
     * should be strictly monotonically increasing, and there should
     * be no gaps between match numbers.
     * 
     * @param ability should be one of abilityBeginner, abilityIntermediate,
     * or abilityExpert; return -1 otherwise.
     */
    public int play (int ability) {
    	if (ability < 1 || ability > 3) {  // invalid ability level
    		return -1;
    	}
    	
    	int index = ability - 1;
    	Lock l = this.matchLocks[index];
    	l.acquire();
    	
    	int playerNum = ++this.matchSizes[index];  // add a new player
    	int playerIndex = this.playerIndexes[index]++; 
    	
    	Condition cv = this.matchConditions[index];
    	if (playerNum == this.matchSize) {  // We have enough players.
    		cv.wakeAll();
    		this.matchNumberLock.acquire();
    		// The array will become [1, 1, 1, 1, 1] after we find 5 people to play the first game.
    		for (int i = 0; i < this.matchSize; i++) {
    			this.matchNumbers.get(index).add(this.globalMatchNumber);  // All the players have the same match number.
    		}
    		this.globalMatchNumber++;
    		this.matchSizes[index] = 0;  // prepare for a new empty match
    		this.matchNumberLock.release();
    	}
    	else {  // We don't have enough players.
    		cv.sleep();
    	}
    	l.release();
    	return this.matchNumbers.get(index).get(playerIndex);
    }
    
    
    /* Test 1 - Threads Calling with Invalid Ability
     * All threads should return -1.
     */
     public static void mTest1 () {
         System.out.println("\n# Running Test 1: Threads Calling with Invalid Ability \n=> All threads should return -1.");
         final GameMatch match = new GameMatch(1);

         // Instantiate the threads
         KThread player1 = new KThread( new Runnable () {
        	 public void run() {
        		 int matchNum = match.play(4);
        		 System.out.println ("Player1 returned with value: " + matchNum + " (INVALID ABILITY).");
        		 Lib.assertTrue(matchNum == -1, "expected match number of -1");
 		    }
 	    });
 	    player1.setName("Player1");
 	    
 	    KThread player2 = new KThread( new Runnable () {
 	    	public void run() {
 	    		int matchNum = match.play(0);
 		        System.out.println ("Player2 returned with value: " + matchNum + " (INVALID ABILITY).");
 		        Lib.assertTrue(matchNum == -1, "expected match number of -1");
 		    }
 	    });
 	    player2.setName("Player2");

         // Run the threads
         player1.fork();
         player2.fork();
         for (int i = 0; i < 10; i++) {
 	        KThread.yield();
 	    }
     }
     
     
     /* Test 2 - N Threads of the Same Ability Call the Play Function
      * All threads should return 1.
      */
      public static void mTest2 () {
          System.out.println("\n# Running Test 2: N Threads of the Same Ability Call the Play Function \n=> All threads should return 1.");
          final GameMatch match = new GameMatch(3);

          // Instantiate the threads
          KThread player1 = new KThread( new Runnable () {
        	  public void run() {
        		  // Players should match with a match number of 1.
        		  int matchNum = match.play(GameMatch.abilityExpert);
	  		      System.out.println ("Player1 returned with value: " + matchNum + " (MATCHED).");
	  		      Lib.assertTrue(matchNum == 1, "expected match number of 1");
        	  }
  	    
          });
          player1.setName("Player1");
          KThread player2 = new KThread( new Runnable () {
        	  public void run() {
        		  // players should match with a match number of 1
        		  int matchNum = match.play(GameMatch.abilityExpert);
        		  System.out.println ("Player2 returned with value: " + matchNum + " (MATCHED).");
        		  Lib.assertTrue(matchNum == 1, "expected match number of 1");
  		    }
  	    });
        player2.setName("Player2");
  	    
        KThread player3 = new KThread( new Runnable () {
        	public void run() {
        		// beginners should match with a match number of 1
        		int matchNum = match.play(GameMatch.abilityExpert);
  		        System.out.println ("Player3 returned with value: " + matchNum + " (MATCHED).");
  		        Lib.assertTrue(matchNum == 1, "expected match number of 1");
  		    }
  	    });
  	    player3.setName("Player3");

  	    //Run the Threads
  	    player1.fork();
  	    player2.fork();
  	    player3.fork();
  	    for (int i = 0; i < 10; i++) {
  	    	KThread.yield();
  	    }
      }
      
      /* Test 3 - One Match for Each Ability
       * The match numbers should increase monotonically.
       */
       public static void mTest3 () {
           System.out.println("\n# Running Test 3: One Match for Each Ability \n=> The match numbers should increase monotonically.");
           final GameMatch match = new GameMatch(1);

           // Instantiate the threads
           KThread expert1 = new KThread( new Runnable () {
        	   public void run() {
        		   int matchNum = match.play(GameMatch.abilityExpert);
        		   System.out.println ("Expert1 returned with value: " + matchNum + " (MATCHED).");
        		   Lib.assertTrue(matchNum > 0, "expected valid match number");
        	   }
   	    
           });
           expert1.setName("Eexpert1");
           
           KThread beginner1 = new KThread( new Runnable () {
        	   public void run() {
        		   int matchNum = match.play(GameMatch.abilityBeginner);
        		   System.out.println ("Beginner1 returned with value: " + matchNum + " (MATCHED).");
        		   Lib.assertTrue(matchNum > 0, "expected valid match number");
        	   }
           });
           beginner1.setName("Beginner1");
   	    
           KThread intPlayer1 = new KThread( new Runnable () {
        	   public void run() {
        		   int matchNum = match.play(GameMatch.abilityIntermediate);
        		   System.out.println ("IntPlayer1 returned with value: " + matchNum + " (MATCHED).");
        		   Lib.assertTrue(matchNum > 0, "expected valid match number");
        	   }
           });
           intPlayer1.setName("IntPlayer1");

           // Run the threads
           expert1.fork();
           beginner1.fork();
           intPlayer1.fork();
           for (int i = 0; i < 10; i++) {
        	   KThread.yield();
           }
       }
       
       /* Test 4 - Multiple Matches of the Same Ability
        * The match numbers should increase monotonically.
        */
        public static void mTest4 () {
            System.out.println("\n# Running Test 4: Multiple Matches of the Same Ability \n=> The match numbers should increase monotonically.");
            final GameMatch match = new GameMatch(1);

            // Instantiate the threads
    	    KThread beginner1 = new KThread( new Runnable () {
    		    public void run() {
    		        int matchNum = match.play(GameMatch.abilityBeginner);
    		        System.out.println ("Beginner1 returned with value: " + matchNum + " (MATCHED).");
    		        Lib.assertTrue(matchNum > 0, "expected valid match number");
    		    }
    	    });
    	    beginner1.setName("Biginner1");
    	    
    	    KThread beginner2 = new KThread( new Runnable () {
    		    public void run() {
    		        int matchNum = match.play(GameMatch.abilityBeginner);
    		        System.out.println ("Beginner2 returned with value: " + matchNum + " (MATCHED).");
    		        Lib.assertTrue(matchNum > 0, "expected valid match number");
    		    }
    	    });
    	    beginner2.setName("Beginner2");
    	    
            KThread beginner3 = new KThread( new Runnable () {
    		    public void run() {
    		        int matchNum = match.play(GameMatch.abilityBeginner);
    		        System.out.println ("Beginner3 returned with value: " + matchNum + " (MATCHED).");
    		        Lib.assertTrue(matchNum > 0, "expected valid match number");
    		    }
    	    });
    	    beginner3.setName("Beginner3");

            // Run the threads
            beginner1.fork();
            beginner2.fork();
            beginner3.fork();
            for (int i = 0; i < 10; i++) {
    	        KThread.yield();
    	    }
        }
        
        /* Test 5 - Different Instances of GameMatch at the Same Time
         * Each GameMatch should work independently.
         */
         public static void mTest5 () {
             System.out.println("\n#Running Test 5: Different Instances of GameMatch at the Same Time \n=> Each GameMatch should work independently.");
             final GameMatch match1 = new GameMatch(1);
             final GameMatch match2 = new GameMatch(1);

             // Instantiate the threads for match1
             KThread beginner1 = new KThread( new Runnable () {
            	 public void run() {
            		 int matchNum = match1.play(GameMatch.abilityBeginner);
            		 System.out.println ("Beginner1 returned with value: " + matchNum + " (MATCHED on GM1).");
            		 Lib.assertTrue(matchNum > 0, "expected valid match number");
            	 }
             });
             beginner1.setName("Beginner1");
     	    
             KThread beginner2 = new KThread( new Runnable () {
            	 public void run() {
            		 int matchNum = match1.play(GameMatch.abilityBeginner);
            		 System.out.println ("Beginner2 returned with value: " + matchNum + " (MATCHED on GM1).");
            		 Lib.assertTrue(matchNum > 0, "expected valid match number");
            	 }
             });
             beginner2.setName("Beginner2");
             
             KThread beginner3 = new KThread( new Runnable () {
            	 public void run() {
            		 int matchNum = match1.play(GameMatch.abilityBeginner);
            		 System.out.println ("Beginner3 returned with value: " + matchNum + " (MATCHED on GM1).");
            		 Lib.assertTrue(matchNum > 0, "expected valid match number");
            	 }
             });
             beginner3.setName("Beginner3");
             
             //match 2 threads
             KThread expert1 = new KThread( new Runnable () {
            	 public void run() {
            		 int matchNum = match2.play(GameMatch.abilityExpert);
            		 System.out.println ("Expert1 returned with value: " + matchNum + " (MATCHED on GM2).");
            		 Lib.assertTrue(matchNum > 0, "expected valid match number");
            	 }
             });
             expert1.setName("Expert1");
     	    
             KThread beginner4 = new KThread( new Runnable () {
     		    public void run() {
     		        int matchNum = match2.play(GameMatch.abilityBeginner);
     		        System.out.println ("Beginner4 returned with value: " + matchNum + " (MATCHED on GM2).");
     		        Lib.assertTrue(matchNum > 0, "expected valid match number");
     		    }
             });
             beginner4.setName("Beginner4");
     	    
             KThread intPlayer1 = new KThread( new Runnable () {
            	 public void run() {
            		 int matchNum = match2.play(GameMatch.abilityIntermediate);
            		 System.out.println ("IntPlayer1 returned with value: " + matchNum + " (MATCHED on GM2).");
            		 Lib.assertTrue(matchNum > 0, "expected valid match number");
            	 }
             });
             intPlayer1.setName("I1_5");

             // Run the threads
             expert1.fork();
             beginner1.fork();
             beginner4.fork();
             beginner2.fork();
             beginner3.fork();
             intPlayer1.fork();
             for (int i = 0; i < 10; i++) {
            	 KThread.yield();
             }
         }     
         
         /* Test 6 - Not Enough Threads to Play
          * No thread returns.
          */
          public static void mTest6 () {
              System.out.println("\n# Running Test 6: Not Enough Threads to Play \n=> No thread returns.");
              final GameMatch match = new GameMatch(2);

              // Instantiate the threads
              KThread intPlayer1 = new KThread( new Runnable () {
            	  public void run() {
            		  int matchNum = match.play(GameMatch.abilityIntermediate);
            		  System.out.println ("IntPlayer1 returned with value: " + matchNum + " (ERROR).");
            		  Lib.assertNotReached("IntPlayer1 should not have matched!");
            	  }
              });
              intPlayer1.setName("IntPlayer1");

              KThread beginner1 = new KThread( new Runnable () {
            	  public void run() {
            		  int matchNum = match.play(GameMatch.abilityBeginner);
            		  System.out.println ("beg1 returned with value: " + matchNum + "; (ERROR)");
            		  Lib.assertNotReached("beg1 should not have matched!");
            	  }
              });
              beginner1.setName("Beginner1");

              // Run the threads
              beginner1.fork();
              intPlayer1.fork();
              for (int i = 0; i < 10; i++) {
            	  KThread.yield();
              }
          }
          
          /* Test 7 - 5 Threads of Same Ability 
           * There should be 2 games of the equal size.
           */
           public static void mTest7 () {
               System.out.println("\n# Running Test 7: 5 Threads of Same Ability \n=> There should be 2 games of the equal size.");
               final GameMatch match = new GameMatch(2);

               // Instantiate the threads
               KThread intPlayer1 = new KThread( new Runnable () {
            	   public void run() {
            		   int matchNum = match.play(GameMatch.abilityIntermediate);
            		   System.out.println ("IntPlayer1 returned with value: " + matchNum + " (MATCHED).");
            		   Lib.assertTrue(matchNum > 0, "expected valid match number");
            	   }
               });
               intPlayer1.setName("IntPlayer1");
               
               KThread intPlayer2 = new KThread( new Runnable () {
            	   public void run() {
            		   int matchNum = match.play(GameMatch.abilityIntermediate);
            		   System.out.println ("IntPlayer2 returned with value: " + matchNum + " (MATCHED).");
            		   Lib.assertTrue(matchNum > 0, "expected valid match number");
            	   }
               });
               intPlayer2.setName("IntPlayer2");
               
               KThread intPlayer3 = new KThread( new Runnable () {
            	   public void run() {
            		   int matchNum = match.play(GameMatch.abilityIntermediate);
            		   System.out.println ("IntPlayer3 returned with value: " + matchNum + " (MATCHED).");
            		   Lib.assertTrue(matchNum > 0, "expected valid match number");
            	   }
               });
               intPlayer3.setName("IntPlayer3");
               
               KThread intPlayer4 = new KThread( new Runnable () {
            	   public void run() {
            		   int matchNum = match.play(GameMatch.abilityIntermediate);
            		   System.out.println ("IntPlayer4 returned with value: " + matchNum + " (MATCHED).");
            		   Lib.assertTrue(matchNum > 0, "expected valid match number");
            	   }
               });
               intPlayer4.setName("IntPlayer4");
               
               KThread intPlayer5 = new KThread( new Runnable () {
            	   public void run() {
            		   int matchNum = match.play(GameMatch.abilityIntermediate);
            		   System.out.println ("int5 returned with value: " + matchNum + " (MATCHED).");
            		   Lib.assertTrue(matchNum > 0, "expected valid match number");
            	   }
               });
               intPlayer5.setName("IntPlayer5");

               // Run the threads
               intPlayer1.fork();
               intPlayer2.fork();
               intPlayer3.fork();
               intPlayer4.fork();
               intPlayer5.fork();
               for (int i = 0; i < 10; i++) {
            	   KThread.yield();
               }
           }  
           
           
           public static void matchTest4 () {
        		final GameMatch match = new GameMatch(2);

        		// Instantiate the threads
        		KThread beg1 = new KThread( new Runnable () {
        			public void run() {
        			    int r = match.play(GameMatch.abilityBeginner);
        			    System.out.println ("beg1 matched");
        			    // beginners should match with a match number of 1
        			    Lib.assertTrue(r == 1, "expected match number of 1");
        			}
        		});
        		beg1.setName("B1");

        		KThread beg2 = new KThread( new Runnable () {
        			public void run() {
        			    int r = match.play(GameMatch.abilityBeginner);
        			    System.out.println ("beg2 matched");
        			    // beginners should match with a match number of 1
        			    Lib.assertTrue(r == 1, "expected match number of 1");
        			}
        		});
        		beg2.setName("B2");

        		KThread int1 = new KThread( new Runnable () {
        			public void run() {
        			    match.play(GameMatch.abilityIntermediate);  // never returns
        			    Lib.assertNotReached("int1 should not have matched!");
        			}
        		    });
        		int1.setName("I1");

        		KThread exp1 = new KThread( new Runnable () {
        			public void run() {
        			    match.play(GameMatch.abilityExpert);  // never returns
        			    Lib.assertNotReached("exp1 should not have matched!");
        			}
        		    });
        		exp1.setName("E1");

        		// Run the threads.  The beginner threads should successfully
        		// form a match, the other threads should not.  The outcome
        		// should be the same independent of the order in which threads
        		// are forked.
        		beg1.fork();
        		int1.fork();
        		exp1.fork();
        		beg2.fork();

        		// Assume join is not implemented, use yield to allow other
        		// threads to run
        		for (int i = 0; i < 10; i++) {
        		    KThread.yield();
        		}
           }
           /* TEST METHODS */
           /* Test Driver - Include all tests to execute inside this method*/
           public static void selfTest() {
           	System.out.println("\n@@@@@@ Beginning GameMatch Tests... @@@@@@");
               System.out.println("\n### Simple Tests ###");
               mTest1();
               mTest2();
               mTest3();
               mTest4();
               mTest5();
               mTest6();
               mTest7();
               System.out.println("\n### Complex Tests ###");

               System.out.println("\n### Default Test Case from Write Up ###");
               matchTest4();
               System.out.println("\n@@@@@@ Ending GameMatch Tests... @@@@@@\n");
           }
}
