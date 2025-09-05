// Simple accumulator with adder
// Parameters: WIDTH (default 8)

module accumulator #(
    parameter WIDTH = 8
) (
    input wire clk,
    input wire rst,
    input wire en,
    input wire [WIDTH-1:0] in,
    output reg [WIDTH-1:0] out
);

    reg [WIDTH-1:0] sum;

    always @(posedge clk) begin
        if (rst) begin
            sum <= 0;
        end else if (en) begin
            sum <= sum + in;
        end
    end

    assign out = sum;

endmodule
