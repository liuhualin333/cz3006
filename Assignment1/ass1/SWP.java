/*===============================================================*
 *  File: SWP.java                                               *
 *                                                               *
 *  This class implements the sliding window protocol            *
 *  Used by VMach class					         *
 *  Uses the following classes: SWE, Packet, PFrame, PEvent,     *
 *                                                               *
 *  Author: Professor SUN Chengzheng                             *
 *          School of Computer Engineering                       *
 *          Nanyang Technological University                     *
 *          Singapore 639798                                     *
 *===============================================================*/

public class SWP {

/*========================================================================*
 the following are provided, do not change them!!
 *========================================================================*/
   //the following are protocol constants.
   public static final int MAX_SEQ = 7; 
   public static final int NR_BUFS = (MAX_SEQ + 1)/2;

   // the following are protocol variables
   private int oldest_frame = 0;
   private PEvent event = new PEvent();  
   private Packet out_buf[] = new Packet[NR_BUFS];

   //the following are used for simulation purpose only
   private SWE swe = null;
   private String sid = null;  

   //Constructor
   public SWP(SWE sw, String s){
      swe = sw;
      sid = s;
   }

   //the following methods are all protocol related
   private void init(){
      for (int i = 0; i < NR_BUFS; i++){
	   out_buf[i] = new Packet();
      }
   }

   private void wait_for_event(PEvent e){
      swe.wait_for_event(e); //may be blocked
      oldest_frame = e.seq;  //set timeout frame seq
   }

   private void enable_network_layer(int nr_of_bufs) {
   //network layer is permitted to send if credit is available
	swe.grant_credit(nr_of_bufs);
   }

   private void from_network_layer(Packet p) {
      swe.from_network_layer(p);
   }

   private void to_network_layer(Packet packet) {
	swe.to_network_layer(packet);
   }

   private void to_physical_layer(PFrame fm)  {
      System.out.println("SWP: Sending frame: seq = " + fm.seq + 
			    " ack = " + fm.ack + " kind = " + 
			    PFrame.KIND[fm.kind] + " info = " + fm.info.data );
      System.out.flush();
      swe.to_physical_layer(fm);
   }

   private void from_physical_layer(PFrame fm) {
      PFrame fm1 = swe.from_physical_layer(); 
	fm.kind = fm1.kind;
	fm.seq = fm1.seq; 
	fm.ack = fm1.ack;
	fm.info = fm1.info;
   }


/*===========================================================================*
 	implement your Protocol Variables and Methods below: 
 *==========================================================================*/

   int nBuffered;
   int ack_expected;
   int frame_expected;
   int next_frame_to_send;
   int too_far;
   int i;
   Pframe r;
   Packet in_buf[] = new Packet[NR_BUFS];
   boolean no_nak = true;
    
   boolean arrived[];
    private void sendFrame(PFrame fm, int frame_expected_num, Packet buffer[]){
        PFrame frame = new PFrame();
        frame.kind = fm.kind;
        if(frame.kind == fm.DATA){frame.info = buffer[]}
        frame.seq = fm.seq;
        frame.ack = (frame_expected_num + MAX_SEQ)%(MAX_SEQ + 1);
        if(frame.kind == fm.NAK){no_nak = false}
        to_physical_layer(frame);
        if(frame.kind == fm.DATA){
            start_timer(frame.seq % NR_BUFS);
        }
        stop_ack_timer();
    }
   public void protocol6() {
       init();
       enable_network_layer(NR_BUFS);
       ack_expected = 0;
       next_frame_to_send = 0;
       frame_expected = 0;
       too_far = NR_BUFS;
       nBuffered=0;
       for(i = 0; i< NR_BUFS; i++){arrived[i]=false};
	while(true) {	
         wait_for_event(event);
	   switch(event.type) {
	      case (PEvent.NETWORK_LAYER_READY):
               nBuffered += 1;
               from_network_layer(out_buf[next_frame_to_send % NR_BUFS]);
               
                   break; 
	      case (PEvent.FRAME_ARRIVAL ):
		   break;	   
              case (PEvent.CKSUM_ERR):
      	           break;  
              case (PEvent.TIMEOUT): 
	           break; 
	      case (PEvent.ACK_TIMEOUT): 
                   break; 
            default: 
		   System.out.println("SWP: undefined event type = " 
                                       + event.type); 
		   System.out.flush();
	   }
      }      
   }

 /* Note: when start_timer() and stop_timer() are called, 
    the "seq" parameter must be the sequence number, rather 
    than the index of the timer array, 
    of the frame associated with this timer, 
   */
 
   private void start_timer(int seq) {
     
   }

   private void stop_timer(int seq) {

   }

   private void start_ack_timer( ) {
      
   }

   private void stop_ack_timer() {
     
   }

}//End of class

/* Note: In class SWE, the following two public methods are available:
   . generate_acktimeout_event() and
   . generate_timeout_event(seqnr).

   To call these two methods (for implementing timers),
   the "swe" object should be referred as follows:
     swe.generate_acktimeout_event(), or
     swe.generate_timeout_event(seqnr).
*/


