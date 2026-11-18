/*
 * LuizFace - mostrador MRE para o W26 (240x240).
 * Desenha o mostrador inteiro: anel, marcacoes, ponteiros e hora digital.
 * Substitui o hello world de bancada; nenhum toque encerra o app.
 */
#include "vmsys.h"
#include "vmtimer.h"
#include "vmgraph.h"
#include "vmstdlib.h"
#include <math.h>

#define SUPPORT_BG
#define PI_ 3.14159265f

VMINT layer_hdl[1];

static VMINT timer_id = -1;
static vm_graphic_color g_color;
static VMINT scr_w, scr_h;

static void handle_sysevt(VMINT message, VMINT param);
static void handle_keyevt(VMINT event, VMINT keycode);
static void handle_penevt(VMINT event, VMINT x, VMINT y);
static void face_draw(void);
static void tick(VMINT tid);

static void setcol(VMUINT c)
{
	g_color.vm_color_565 = c;
	vm_graphic_setcolor(&g_color);
}

static void hline(VMINT x, VMINT y, VMINT w)
{
	vm_graphic_fill_rect_ex(layer_hdl[0], x, y, w, 1);
}

static void thick_line(VMINT x0, VMINT y0, VMINT x1, VMINT y1, VMINT t)
{
	VMINT i, o = t / 2;
	for (i = -o; i <= o; i++)
		vm_graphic_line_ex(layer_hdl[0], x0 + i, y0, x1 + i, y1);
}

static void ring(VMINT cx, VMINT cy, VMINT r)
{
	vm_graphic_ellipse_ex(layer_hdl[0], cx - r, cy - r, r * 2, r * 2);
	vm_graphic_ellipse_ex(layer_hdl[0], cx - r + 1, cy - r + 1, r * 2 - 2, r * 2 - 2);
}

static void hand(VMINT cx, VMINT cy, VMINT angle_x10, VMINT len, VMINT t, VMUINT c)
{
	float a = angle_x10 * 0.1f * PI_ / 180.0f;
	VMINT x1 = cx + (VMINT)(sinf(a) * len);
	VMINT y1 = cy - (VMINT)(cosf(a) * len);
	setcol(c);
	thick_line(cx, cy - 2, x1, y1, t);
}

static void put_time(VMINT cx, VMINT cy, VMINT hour, VMINT min, VMINT day, VMINT mon)
{
	short t[8];
	VMINT tw, i;
	char hh[3], mm[3], dd[3], mo[3];
	VMINT y = cy;

	hh[0] = '0' + hour / 10; hh[1] = '0' + hour % 10; hh[2] = 0;
	mm[0] = '0' + min / 10;  mm[1] = '0' + min % 10;  mm[2] = 0;
	dd[0] = '0' + day / 10;  dd[1] = '0' + day % 10;  dd[2] = 0;
	mo[0] = '0' + mon / 10;  mo[1] = '0' + mon % 10;  mo[2] = 0;

	vm_graphic_set_font(VM_LARGE_FONT);
	t[0] = hh[0]; t[1] = hh[1]; t[2] = ':'; t[3] = mm[0]; t[4] = mm[1]; t[5] = 0;
	tw = vm_graphic_get_string_width(t);
	setcol(0xFFFF);
	vm_graphic_textout_to_layer(layer_hdl[0], cx - tw / 2, y, t, 5);

	vm_graphic_set_font(VM_SMALL_FONT);
	t[0] = dd[0]; t[1] = dd[1]; t[2] = '/'; t[3] = mo[0]; t[4] = mo[1]; t[5] = 0;
	tw = vm_graphic_get_string_width(t);
	setcol(0x7BEF);
	vm_graphic_textout_to_layer(layer_hdl[0], cx - tw / 2, y + 34, t, 5);

	t[0] = 'L'; t[1] = 'U'; t[2] = 'I'; t[3] = 'Z'; t[4] = 0;
	tw = vm_graphic_get_string_width(t);
	setcol(0x07FF);
	vm_graphic_textout_to_layer(layer_hdl[0], cx - tw / 2, y + 52, t, 4);
}

static void face_draw(void)
{
	vm_time_t now;
	VMINT cx, cy, r, i, ang, len;

	if (layer_hdl[0] == -1)
		return;

	scr_w = vm_graphic_get_screen_width();
	scr_h = vm_graphic_get_screen_height();
	cx = scr_w / 2;
	cy = scr_h / 2;
	r = (scr_w < scr_h ? scr_w : scr_h) / 2 - 3;

	setcol(0x0000);
	vm_graphic_fill_rect_ex(layer_hdl[0], 0, 0, scr_w, scr_h);

	setcol(0x2945);
	ring(cx, cy, r);
	setcol(0x18E3);
	ring(cx, cy, r - 8);

	for (i = 0; i < 12; i++) {
		float a = i * 30.0f * PI_ / 180.0f;
		VMINT x0 = cx + (VMINT)(sinf(a) * (r - 16));
		VMINT y0 = cy - (VMINT)(cosf(a) * (r - 16));
		VMINT x1 = cx + (VMINT)(sinf(a) * (r - 24));
		VMINT y1 = cy - (VMINT)(cosf(a) * (r - 24));
		if (i % 3 == 0)
			setcol(0xFFFF);
		else
			setcol(0x6B4D);
		thick_line(x0, y0, x1, y1, (i % 3 == 0) ? 3 : 1);
	}

	if (vm_get_time(&now) != 0) {
		now.hour = 0; now.min = 0; now.sec = 0;
		now.day = 1; now.mon = 1;
	}

	ang = (now.hour % 12) * 300 + now.min * 5;      /* decimos de grau */
	hand(cx, cy, ang, r * 50 / 100, 5, 0xFFFF);

	len = now.sec < 0 ? 0 : now.sec;
	ang = now.min * 60 + len;
	hand(cx, cy, ang, r * 72 / 100, 3, 0x07FF);

	hand(cx, cy, now.sec * 60, r * 84 / 100, 1, 0xF800);

	setcol(0xFFFF);
	vm_graphic_fill_ellipse_ex(layer_hdl[0], cx - 3, cy - 3, 7, 7);

	put_time(cx, cy + r * 45 / 100, now.hour, now.min, now.day, now.mon);

	vm_graphic_flush_layer(layer_hdl, 1);
}

static void tick(VMINT tid)
{
	(void)tid;
	face_draw();
}

static void handle_sysevt(VMINT message, VMINT param)
{
	(void)param;
	switch (message) {
	case VM_MSG_CREATE:
		layer_hdl[0] = -1;
		scr_w = vm_graphic_get_screen_width();
		scr_h = vm_graphic_get_screen_height();
		if (timer_id == -1)
			timer_id = vm_create_timer(1000, tick);
		break;
	case VM_MSG_ACTIVE:
	case VM_MSG_PAINT:
		if (layer_hdl[0] == -1) {
			layer_hdl[0] = vm_graphic_create_layer(0, 0, scr_w, scr_h, -1);
			vm_graphic_set_clip(0, 0, scr_w, scr_h);
		}
		face_draw();
		break;
	case VM_MSG_HIDE:
	case VM_MSG_INACTIVE:
		if (layer_hdl[0] != -1) {
			vm_graphic_delete_layer(layer_hdl[0]);
			layer_hdl[0] = -1;
		}
		break;
	case VM_MSG_QUIT:
		if (timer_id != -1) {
			vm_delete_timer(timer_id);
			timer_id = -1;
		}
		if (layer_hdl[0] != -1) {
			vm_graphic_delete_layer(layer_hdl[0]);
			layer_hdl[0] = -1;
		}
		vm_res_deinit();
		break;
	}
}

static void handle_keyevt(VMINT event, VMINT keycode)
{
	(void)event;
	(void)keycode;
}

static void handle_penevt(VMINT event, VMINT x, VMINT y)
{
	(void)event;
	(void)x;
	(void)y;
}

void vm_main(void)
{
	layer_hdl[0] = -1;
	vm_reg_sysevt_callback(handle_sysevt);
	vm_reg_keyboard_callback(handle_keyevt);
	vm_reg_pen_callback(handle_penevt);
	vm_res_init();
}
