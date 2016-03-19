import java.util.Scanner;
import java.io.PrintStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;


class HelloWorld{
	public static void main(String[] argv) throws Exception
	{
		//Scanner in = new Scanner(System.in);
	    //int x = in.nextInt();
	    HelloWorld hw = new HelloWorld();
	    System.out.println("Simple if");
		hw.simpleif(2);
		hw.simpleif(2);
		hw.simpleif(1);
		hw.simpleif(1);
		hw.simpleif(1);
		hw.simpleif(1);

		System.out.println("main2");
		hw.main2();
		hw.main2();

		System.out.println("main3");
		hw.main3();

		System.out.println("multi_Ret");
		hw.multi_ret(2);
		hw.multi_ret(2);
		hw.multi_ret(190);

		System.out.println("onw while");
		hw.one_while(2);
		hw.one_while(1);

		System.out.println("two while");
		hw.two_while(2);
		hw.two_while(1);

		System.out.println("nested while");
		hw.nested_while(4);

		System.out.println("month");
		hw.func_month(3);
		hw.func_month(4);

		System.out.println("another example");
		hw.another_example(5);

	}

	public void simpleif(int x){
		if(x==2){
			System.out.println(x);
		} else{
			System.out.println(x);
		}
	}

	public void exception_func(){
		PrintStream printStream;
			try {
				printStream = new PrintStream(new FileOutputStream("output.txt"));
				System.setOut(printStream);
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
	}

	public void simpleif_exit(int x){
		if(x==2){
			System.exit(x);
		} else if(x==3){
			System.exit(x);
		}
		System.out.println(x);
	}

	public void main2()
	{
		Scanner in = new Scanner(System.in);
	    int x = in.nextInt();
	    int y = in.nextInt();
		if(y==1) {
			if(x==0){
				x=x+1;
			}
			else {
				if(x==1){
					x=x+2;
				}
			}
		}
		x=x+3;
		if(x==3){
			x=x+4;
		}
		x=x+5;
		System.out.println(x);
	}

	public void main3()
	{
		Scanner in = new Scanner(System.in);
	    int x = in.nextInt();
	    int y = in.nextInt();
		if(y==1) {
			if(x==0){
				x=x+1;
			}
			else {
				if(x==1){
					x=x+2;
				}
			}
		}else{
			x=x+10;
		}
		x=x+3;
		if(x==3){
			x=x+4;
		}
		x=x+5;
		System.out.println(x);
	}

	public int multi_ret(int x)
	{
		if(x==1)
			return 2;
		else
			return 3;
	}

	public void one_while(int x)
	{
		while(x==5){
			x++;
		}
		System.out.println(x);
	}

	public void two_while(int x)
	{
		while(x==5){
			x++;
		}

		while(x==7){
			x++;
		}
		System.out.println(x);
	}

	public void nested_while(int x)
	{
		while(x==5){
			while(x==12){
				x++;
		}
		}
		System.out.println(x);
	}

	public void one_exit(int x){
		System.exit(3);
	}

	public void two_exits(int x){
		if(x==1)
			System.exit(0);
		else
			System.exit(2);

	}

	public void func_month(int month){
        String monthString;
        switch (month) {
            case 1:  monthString = "January";
                     break;
            case 2:  monthString = "February";
                     break;
            case 3:  monthString = "March";
                     break;
            case 4:  monthString = "April";
                     break;
            case 5:  monthString = "May";
                     break;
            case 6:  monthString = "June";
                     break;
            case 7:  monthString = "July";
                     break;
            case 8:  monthString = "August";
                     break;
            case 9:  monthString = "September";
                     break;
            case 10: monthString = "October";
                     break;
            case 11: monthString = "November";
                     break;
            case 12: monthString = "December";
                     break;
            default: monthString = "Invalid month";
                     break;
        }
	}

	public void another_example(int x){
        if(x==3){
        	while(x<5){
        		if(x==4){
        			x++;
        		}else{
        			x--;
        		}
        		x++;
        	}
        }

        if(x==4){
        	x++;
        }else{
        	x--;
        }
	}
}