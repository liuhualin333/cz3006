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
import javax.swing.Timer
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
      arrived[i] = false;
    }
       enable_network_layer(NR_BUFS);
       ack_expected = 0;
       next_frame_to_send = 0;
       frame_expected = 0;
       too_far = NR_BUFS;
       nBuffered=0;
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
  private Packet in_buf[] = new Packet[NR_BUFS];
   
  private int ack_expected;              //lower edge of sender's window
  private int next_frame_to_send;          //upper edge of sender's window
  private int frame_expected;            //lower edge of receiver's window
  private int too_far;               //upper edge of receiver's window
  
  private int i;                 //index into buffer pool
  private PFrame r;                //scratch variable
   
  private boolean arrived[] = new boolean[NR_BUFS];
  private int nbuffered;
  private boolean no_nak = true;  
   
  public static final int DATA = 0;
  public static final int ACK  = 1;
  public static final int NAK  = 2;
  public static final String[] KIND = {"DATA", 
                "ACK", 
                "NAK" };
   
    
  private void sendFrame(int fk, int frame_nr, int frame_expected, Packet buffer[]){
    PFrame frame = new PFrame();
    frame.kind = fk;
    if(frame.kind == frame.DATA)
      frame.info = buffer[frame_nr % NR_BUFS];
    frame.seq = frame_nr;
    frame.ack = (frame_expected + MAX_SEQ) % (MAX_SEQ + 1);
    if(frame.kind == frame.NAK)
      no_nak = false;
    to_physical_layer(frame);
    if(frame.kind == frame.DATA)
      start_timer(frame_nr % NR_BUFS);
    stop_ack_timer();
  }
  
  public void protocol6() {
    init();
    while(true) {
      wait_for_event(event);
	    switch(event.type) {
        case (PEvent.NETWORK_LAYER_READY):          
          nbuffered += 1;
            from_network_layer(out_buf[next_frame_to_send % NR_BUFS]);
            send_frame(DATA, next_frame_to_send, frame_expected, out_buf);
            inc(next_frame_to_send);
            break;           
        case (PEvent.FRAME_ARRIVAL ):
          from_physical_layer(r);
            if (r.kind==DATA){
              if ((r.seq!=frame_expected)&&no_nak)
                send_frame(NAK, 0, frame_expected, out_buf);
              else
                start_ack_timer();
              
              if (between(frame_expected, r.seq, too_far)&&(arrived[r.seq % NR_BUFS]==false)){
                arrived[r.seq % NR_BUFS] = true;
                in_buf[r.seq % NR_BUFS] = r.info;
                while (arrived[frame_expected % NR_BUFS]) {
                  to_network_layer(in_buf[frame_expected % NR_BUFS]);
                  no_nak = true;
                  arrived[frame_expected % NR_BUFS] = false;
                  inc(frame_expected);
                  inc(too_far);
                  start_ack_timer();
                  }               
              }             
            }
            if ((r.kind==NAK)&&between(ack_expected, (r.ack+1)%(MAX_SEQ+1), next_frame_to_send))
              send_frame(DATA, (r.ack+1)%(MAX_SEQ+1), frame_expected, out_buf);
            
            while (between(ack_expected, r.ack, next_frame_to_send)) {
              nbuffered -= 1;
              stop_timer(ack_expected % NR_BUFS);
              inc(ack_expected);
              }
          break;     
          case (PEvent.CKSUM_ERR):
            if (no_nak) {
        send_frame(NAK, 0, frame_expected, out_buf);
        }
              break;  
          case (PEvent.TIMEOUT):
            send_frame(DATA, oldest_frame, frame_expected, out_buf);
            break; 
        case (PEvent.ACK_TIMEOUT): 
          send_frame(ACK, 0, frame_expected, out_buf);
          break; 
          default:
            System.out.println("SWP: undefined event type = " + event.type);
            System.out.flush();
     }
    }      
  }

  private boolean between(int a, int b, int c) {
    return ((a<=b)&&(b<c))||((c<a)&&(a<=b))||((b<c)&&(c<a));
  }

  private void inc(int number_frame) {
    number_frame = (number_frame + 1) % 8; 
  }


 /* Note: when start_timer() and stop_timer() are called, 
    the "seq" parameter must be the sequence number, rather 
    than the index of the timer array, 
    of the frame associated with this timer, 
   */
  Timer timers = new Timer[NR_BUFS];
  Timer acktimer;
  private void start_timer(int seq) {
    ActionListener taskPerformer = new ActionListener() {
      public void actionPerformed(ActionEvent evt) {
        swe.generate_timeout_event(seq);
      }
    };
    timers[seq] = new Timer(1000，taskPerformer);
    timers[seq].setRepeats(false);
    timers[seq].start();
  }

  private void stop_timer(int seq) {
    timers[seq].stop();
  }

  private void start_ack_timer( ) {
    ActionListener taskPerformer = new ActionListener() {
      public void actionPerformed(ActionEvent evt) {
        swe.generate_timeout_event(seq);
      }
    };
    acktimer = new Timer(1000, taskPerformer);
    acktimer.setRepeats(false);
    acktimer.start();
  }

  private void stop_ack_timer() {
    acktimer.stop();
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