
#include "vmsys.h"
#include "vmgraph.h"
#include "vmstdlib.h"

VMINT layer_hdl[1];
static void handle_sysevt(VMINT message, VMINT param);
static void draw_red(void);

static void draw_red(void)
{
	vm_graphic_color c;
	if (layer_hdl[0] == -1)
		return;
	c.vm_color_565 = 0xF800;
	vm_graphic_setcolor(&c);
	vm_graphic_fill_rect_ex(layer_hdl[0], 0, 0, vm_graphic_get_screen_width(), vm_graphic_get_screen_height());
	c.vm_color_565 = 0xFFFF;
	vm_graphic_setcolor(&c);
	vm_graphic_fill_rect_ex(layer_hdl[0], 0, vm_graphic_get_screen_height() / 2 - 10, vm_graphic_get_screen_width(), 20);
	vm_graphic_flush_layer(layer_hdl, 1);
}

static void handle_sysevt(VMINT message, VMINT param)
{
	(void)param;
	switch (message) {
	case VM_MSG_CREATE:
		layer_hdl[0] = -1;
		break;
	case VM_MSG_ACTIVE:
	case VM_MSG_PAINT:
		if (layer_hdl[0] == -1) {
			layer_hdl[0] = vm_graphic_create_layer(0, 0, vm_graphic_get_screen_width(), vm_graphic_get_screen_height(), -1);
			vm_graphic_set_clip(0, 0, vm_graphic_get_screen_width(), vm_graphic_get_screen_height());
		}
		draw_red();
		break;
	case VM_MSG_HIDE:
	case VM_MSG_INACTIVE:
		if (layer_hdl[0] != -1) { vm_graphic_delete_layer(layer_hdl[0]); layer_hdl[0] = -1; }
		break;
	case VM_MSG_QUIT:
		if (layer_hdl[0] != -1) { vm_graphic_delete_layer(layer_hdl[0]); layer_hdl[0] = -1; }
		break;
	}
}

void vm_main(void)
{
	layer_hdl[0] = -1;
	vm_reg_sysevt_callback(handle_sysevt);
}
