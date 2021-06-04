
module tb_sdram_controller (
  input               io_bus_cmd_valid,
  output              io_bus_cmd_ready,
  input      [23:0]   io_bus_cmd_payload_address,
  input               io_bus_cmd_payload_write,
  input      [15:0]   io_bus_cmd_payload_data,
  input      [2:0]    io_bus_cmd_payload_burstLen,
  input      [1:0]    io_bus_cmd_payload_mask,
  input      [3:0]    io_bus_cmd_payload_opId,
  input               io_bus_cmd_payload_last,
  output              io_bus_rsp_valid,
  input               io_bus_rsp_ready,
  output     [15:0]   io_bus_rsp_payload_data,
  output     [3:0]    io_bus_rsp_payload_opId,
  output              io_bus_rsp_payload_last,
  output              io_initDone,
  input               clk,
  input               reset
);
  wire                controller_io_bus_cmd_ready;
  wire                controller_io_bus_rsp_valid;
  wire       [15:0]   controller_io_bus_rsp_payload_data;
  wire       [3:0]    controller_io_bus_rsp_payload_opId;
  wire                controller_io_bus_rsp_payload_last;
  wire       [12:0]   controller_io_sdram_ADDR;
  wire       [1:0]    controller_io_sdram_BA;
  wire                controller_io_sdram_CASn;
  wire                controller_io_sdram_CKE;
  wire                controller_io_sdram_CSn;
  wire       [1:0]    controller_io_sdram_DQM;
  wire                controller_io_sdram_RASn;
  wire                controller_io_sdram_WEn;
  wire       [15:0]   controller_io_sdram_DQ_write;
  wire       [15:0]   controller_io_sdram_DQ_writeEnable;
  wire       [15:0]   sdramDevice_DQ_read;

  SdramController controller (
    .io_bus_cmd_valid               (io_bus_cmd_valid                          ), //i
    .io_bus_cmd_ready               (controller_io_bus_cmd_ready               ), //o
    .io_bus_cmd_payload_address     (io_bus_cmd_payload_address[23:0]          ), //i
    .io_bus_cmd_payload_write       (io_bus_cmd_payload_write                  ), //i
    .io_bus_cmd_payload_data        (io_bus_cmd_payload_data[15:0]             ), //i
    .io_bus_cmd_payload_burstLen    (io_bus_cmd_payload_burstLen[2:0]          ), //i
    .io_bus_cmd_payload_mask        (io_bus_cmd_payload_mask[1:0]              ), //i
    .io_bus_cmd_payload_opId        (io_bus_cmd_payload_opId[3:0]              ), //i
    .io_bus_cmd_payload_last        (io_bus_cmd_payload_last                   ), //i
    .io_bus_rsp_valid               (controller_io_bus_rsp_valid               ), //o
    .io_bus_rsp_ready               (io_bus_rsp_ready                          ), //i
    .io_bus_rsp_payload_data        (controller_io_bus_rsp_payload_data[15:0]  ), //o
    .io_bus_rsp_payload_opId        (controller_io_bus_rsp_payload_opId[3:0]   ), //o
    .io_bus_rsp_payload_last        (controller_io_bus_rsp_payload_last        ), //o
    .io_sdram_ADDR                  (controller_io_sdram_ADDR[12:0]            ), //o
    .io_sdram_BA                    (controller_io_sdram_BA[1:0]               ), //o
    .io_sdram_DQ_read               (sdramDevice_DQ_read[15:0]                 ), //i
    .io_sdram_DQ_write              (controller_io_sdram_DQ_write[15:0]        ), //o
    .io_sdram_DQ_writeEnable        (controller_io_sdram_DQ_writeEnable[15:0]  ), //o
    .io_sdram_DQM                   (controller_io_sdram_DQM[1:0]              ), //o
    .io_sdram_CASn                  (controller_io_sdram_CASn                  ), //o
    .io_sdram_CKE                   (controller_io_sdram_CKE                   ), //o
    .io_sdram_CSn                   (controller_io_sdram_CSn                   ), //o
    .io_sdram_RASn                  (controller_io_sdram_RASn                  ), //o
    .io_sdram_WEn                   (controller_io_sdram_WEn                   ), //o
    .io_initDone                    (io_initDone                               ), //o
    .clk                            (clk                                       ), //i
    .reset                          (reset                                     )  //i
  );

  wire [15:0] io_sdram_DQ;
  assign sdramDevice_DQ_read = io_sdram_DQ;
  assign io_sdram_DQ = controller_io_sdram_DQ_writeEnable ? controller_io_sdram_DQ_write : 16'bZZZZZZZZZZZZZZZZ;

  // sdram_model_plus #(
  //   .addr_bits        (12           ),   // 地址位宽
  //   .data_bits        (16           ),   // 数据位宽
  //   .col_bits         (9            ),   // col地址位宽A0-A8
  //   .mem_sizes        (2*1024*1024-1)    // 2M
  // ) sdramDeivce (
  //   .Debug             (1'b1                                      ), //i
  mt48lc16m16a2 sdramDevice (
    .Clk               (~clk                                      ), //i
    .Addr              (controller_io_sdram_ADDR[12:0]            ), //i
    .Ba                (controller_io_sdram_BA[1:0]               ), //i
    .Dq                (io_sdram_DQ                               ), //io
    .Dqm               (controller_io_sdram_DQM[1:0]              ), //i
    .Cas_n             (controller_io_sdram_CASn                  ), //i
    .Cke               (controller_io_sdram_CKE                   ), //i
    .Cs_n              (controller_io_sdram_CSn                   ), //i
    .Ras_n             (controller_io_sdram_RASn                  ), //i
    .We_n              (controller_io_sdram_WEn                   )  //i
  );
  assign io_bus_cmd_ready = controller_io_bus_cmd_ready;
  assign io_bus_rsp_valid = controller_io_bus_rsp_valid;
  assign io_bus_rsp_payload_data = controller_io_bus_rsp_payload_data;
  assign io_bus_rsp_payload_opId = controller_io_bus_rsp_payload_opId;
  assign io_bus_rsp_payload_last = controller_io_bus_rsp_payload_last;

  initial begin
    $dumpfile ("wave.vcd");
    $dumpvars;
    #1;
  end
endmodule
