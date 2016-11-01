package activemq.xmg.com.hellomqtt.activity;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import activemq.xmg.com.hellomqtt.R;
import activemq.xmg.com.hellomqtt.service.MQService;
import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * 参考案例：https://github.com/JesseFarebro/android-mqtt
 */
public class MainActivity extends AppCompatActivity {

    @Bind(R.id.btn_start_connect)
    Button btnStartConnect;
    @Bind(R.id.btn_end_connect)
    Button btnEndConnect;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        setListener();
    }

    private void setListener() {
        btnStartConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MQService.actionStart(MainActivity.this);
            }
        });

        btnEndConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MQService.actionStop(MainActivity.this);
            }
        });
    }
}
