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
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
//import java.util.Timer;
//import java.util.TimerTask;
public class SWP implements ActionListener{

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
*============================================================================*/
    private Packet in_buf[] = new Packet[NR_BUFS]; //buffer for packets received
   
    private int ack_expected = 0;           //lower edge of sender's window
    private int next_frame_to_send = 0;     //upper edge of sender's window
    private int frame_expected = 0;         //lower edge of receiver's window
    private int too_far = NR_BUFS;          //upper edge of receiver's window
    private int nbuffered = 0;              //number of buffers used now
    private int i;                          //index into buffer pool
    private PFrame r;                       //scratch variable

    private boolean arrived[] = new boolean[NR_BUFS]; //array recording whether the frame arrives or not
    private boolean no_nak = true;  //boolean variable recording whether a nak is received or not

    //Status code
    public static final int DATA = 0;
    public static final int ACK  = 1;
    public static final int NAK  = 2;
    public static final String[] KIND = {"DATA", 
                                        "ACK", 
                                        "NAK"};
   
    //function for sending a frame or ack or nak
    private void sendFrame(int fk, int frame_nr, int frame_expected, Packet buffer[]){
    	//Assemble the frame
        PFrame frame = new PFrame();
        frame.kind = fk;
        if(frame.kind == frame.DATA){
            frame.info = buffer[frame_nr % NR_BUFS];
        }
        frame.seq = frame_nr;
        frame.ack = (frame_expected + MAX_SEQ) % (MAX_SEQ + 1);
        if(frame.kind == frame.NAK){
            no_nak = false;
        }
        //Send the frame to physical layer
        to_physical_layer(frame);
        //Start a frame timer if it is a data frame
        if(frame.kind == frame.DATA)
            start_timer(frame_nr);
        //Stop the ack_timer
        stop_ack_timer();
    }
  
    public void protocol6() {
        init();
        //enable sending NR_BUFS number of frames
        enable_network_layer(NR_BUFS);
        for (int i = 0; i < NR_BUFS; i++){
            arrived[i] = false;
            in_buf[i] = new Packet();
        }
        r = new PFrame(); 

        while(true) {
            wait_for_event(event);
            switch(event.type) {
            	//Network layer ready
                case (PEvent.NETWORK_LAYER_READY):          
                    nbuffered++;
                    //Fetch and send a frame
                    from_network_layer(out_buf[next_frame_to_send % NR_BUFS]);
                    sendFrame(DATA, next_frame_to_send, frame_expected, out_buf);
                    next_frame_to_send = inc(next_frame_to_send);
                    break;
                //Frame arrives
                case (PEvent.FRAME_ARRIVAL ):
                    //Fetch a frame from physical layer
                    from_physical_layer(r);
                    //If the frame is a data frame
                    if (r.kind==DATA){
                    	//Not the frame we expect(not in order) and no nak was sent before
                        if ((r.seq!=frame_expected)&&no_nak){
                            sendFrame(NAK, 0, frame_expected, out_buf);
                        }
                        else //It is the frame we want! Start ack timer!
                            start_ack_timer();
                        //The frame sequential number is in the receiver window and haven't arrived before
                        if (between(frame_expected, r.seq, too_far)&&(arrived[r.seq % NR_BUFS]==false)){
                            arrived[r.seq % NR_BUFS] = true;
                            in_buf[r.seq % NR_BUFS] = r.info;
                            //Check if the arrived message is in order
                            while (arrived[frame_expected % NR_BUFS]) {
                            	//If in order, transmit them to network layer
                                to_network_layer(in_buf[frame_expected % NR_BUFS]);
                                no_nak = true;
                                arrived[frame_expected % NR_BUFS] = false;
                                frame_expected = inc(frame_expected);
                                too_far = inc(too_far);
                                start_ack_timer();
                            }               
                        }          
                    }
                    //If that is a NAK of a frame in the sender window
                    if ((r.kind==NAK)&&between(ack_expected, inc(r.ack), next_frame_to_send))
                        sendFrame(DATA, inc(r.ack), frame_expected, out_buf);
                    //If the ACK is in the sender Window, stop frame timer of all frames before ACKed frame
                    while (between(ack_expected, r.ack, next_frame_to_send)) {
                        nbuffered--;
                        stop_timer(ack_expected % NR_BUFS);
                        ack_expected = inc(ack_expected);
                        //each time a frame is ACKed, the network layer is allowed to give credit to a new frame
                        enable_network_layer(1);          //changed here!
                    }
                    break;
                //Checksum error, send nak    
                case (PEvent.CKSUM_ERR):
                    if (no_nak){
                        sendFrame(NAK, 0, frame_expected, out_buf);
                    }
                    break;  
                //Frame timer timeout, resend the frame
                case (PEvent.TIMEOUT):
                    sendFrame(DATA, oldest_frame, frame_expected, out_buf);
                    break;
                //ACK timer timeout, resend ACK
                case (PEvent.ACK_TIMEOUT): 
                    sendFrame(ACK, 0, frame_expected, out_buf);
                    break; 
                //Undefined event type
                default:
                    System.out.println("SWP: undefined event type = " + event.type);
                    System.out.flush();
            }
        }
    }
    //Check whether it is between a range
    private boolean between(int a, int b, int c) {
        return ((a<=b)&&(b<c))||((c<a)&&(a<=b))||((b<c)&&(c<a));
    }

    //Function for incrementing a number
    private int inc(int number_frame) {
        return (number_frame + 1) % 8; 
    }

    /*===========================================================
        Note: when start_timer() and stop_timer() are called, 
        the "seq" parameter must be the sequence number, rather 
        than the index of the timer array, 
        of the frame associated with this timer, 
    ===========================================================*/

    //Timer for frames
    Timer timers[] = new Timer[NR_BUFS];

    //Timer for ACK
    Timer acktimer = new Timer(500, this);
    
    //Timer Methods
    //Start frame timer
    private void start_timer(int seq) {
        final int seqnum = seq;
        //Ensure no other timer for this sequential number is still running
        stop_timer(seq);
        //Action listener for a timer. When the delay is reached, invoke actionPerformed
        ActionListener taskPerformer = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                //generate timeout event
                swe.generate_timeout_event(seqnum);
            }
        };
        timers[seq%NR_BUFS] = new Timer(1000, taskPerformer);
        //Timer go off only once
        timers[seq%NR_BUFS].setRepeats(false);
        timers[seq%NR_BUFS].start();
    }
    
    //Stop frame timer
    private void stop_timer(int seq) {
    	//In case we are stopping a timer that doesn't exist
        try {
            timers[seq%NR_BUFS].stop(); 
        } catch (Exception e){}
    }

    //Start ack timer
    private void start_ack_timer( ) {
    	//Ensure no other ack timer is running before generate a new ack timer
        stop_ack_timer();
        //Action listener for a timer. When the delay is reached, invoke actionPerformed
        ActionListener taskPerformer = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {
                //generate ack timeout event
                swe.generate_acktimeout_event();
            }
        };
        acktimer = new Timer(500, taskPerformer);
        //Timer go off only once
        acktimer.setRepeats(false);
        acktimer.start();
    }

    //Stop ack timer
    private void stop_ack_timer() {
    	//In case we are stopping a timer that doesn't exist
        try{
            acktimer.stop();
        }catch (Exception e){}
    }

    //Actionlistener for the first ack timer
    public void actionPerformed (ActionEvent evt){
        swe.generate_acktimeout_event();
        System.out.println("Action Performed");
    }
    
}   //End of class

/* Note: In class SWE, the following two public methods are available:
   . generate_acktimeout_event() and
   . generate_timeout_event(seqnr).

   To call these two methods (for implementing timers),
   the "swe" object should be referred as follows:
     swe.generate_acktimeout_event(), or
     swe.generate_timeout_event(seqnr).
*/