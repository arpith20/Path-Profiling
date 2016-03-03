import java.util.Scanner;
class HelloWorld{
	public static void main(String[] argv) throws Exception
	{
		Scanner in = new Scanner(System.in);
	    int x = in.nextInt();
		simpleif(2);
		simpleif(1);
		simpleif(1);
	}

	public static void simpleif(int x){
		if(x==2){
			System.out.println(x);
		} else{
			System.out.println(x);
		}
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
}